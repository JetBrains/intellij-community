@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.plugins.textmate

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.SlowOperations
import com.intellij.util.containers.Interner
import com.intellij.util.ui.EDT
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.textmate.TextMateService.LOG
import org.jetbrains.plugins.textmate.api.TextMateBundleProvider
import org.jetbrains.plugins.textmate.bundles.*
import org.jetbrains.plugins.textmate.bundles.BundleType.Companion.detectBundleType
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings
import org.jetbrains.plugins.textmate.editor.fileNameExtensions
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.preferences.*
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTable
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils
import java.lang.Runnable
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.coroutineContext
import kotlin.io.path.absolutePathString
import kotlin.time.Duration.Companion.minutes

private class InitializedState(
  @JvmField val customHighlightingColors: Map<CharSequence, TextMateTextAttributesAdapter>,
  @JvmField val extensionMapping: Map<TextMateFileNameMatcher, CharSequence>,
  @JvmField val syntaxTable: TextMateSyntaxTable = TextMateSyntaxTable(),
  @JvmField val snippetRegistry: SnippetsRegistryImpl,
  @JvmField val preferenceRegistry: PreferencesRegistryImpl,
  @JvmField val shellVariablesRegistry: ShellVariablesRegistryImpl,
)

private fun createEmptyInitializedState(): InitializedState {
  return InitializedState(
    customHighlightingColors = java.util.Map.of(),
    extensionMapping = java.util.Map.of(),
    syntaxTable = TextMateSyntaxTable(),
    snippetRegistry = SnippetsRegistryImpl(),
    preferenceRegistry = PreferencesRegistryImpl(),
    shellVariablesRegistry = ShellVariablesRegistryImpl(),
  )
}

class TextMateServiceImpl(private val coroutineScope: CoroutineScope) : TextMateService() {
  private var builtinBundlesDisabled = false

  private val initJob: AtomicReference<Deferred<InitializedState>>

  init {
    val app = ApplicationManager.getApplication()
    val checkCancelled = if (app == null || app.isUnitTestMode) null else Runnable { ProgressManager.checkCanceled() }
    SyntaxMatchUtils.setCheckCancelledCallback(checkCancelled)

    initJob = AtomicReference<Deferred<InitializedState>>(coroutineScope.async {
      try {
        registerBundles(fireEvents = false, oldState = null)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.error(e)
        val s = createEmptyInitializedState()
        s
      }
    })
  }

  override fun reloadEnabledBundles() {
    reinit()
  }

  private fun reinit(): Deferred<InitializedState> {
    val job = initJob.updateAndGet { oldJob ->
      oldJob?.cancel()
      coroutineScope.async(start = CoroutineStart.LAZY) {
        oldJob?.cancelAndJoin()
        try {
          @Suppress("OPT_IN_USAGE")
          registerBundles(fireEvents = true, oldState = runCatching { oldJob?.getCompleted() }.getOrNull())
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          LOG.error(e)
          val s = createEmptyInitializedState()
          s
        }
      }
    }!!
    job.start()
    return job
  }

  private suspend fun registerBundles(fireEvents: Boolean, oldState: InitializedState?): InitializedState {
    val interner = ConcurrentInterner<CharSequence>()

    val syntaxTable = TextMateSyntaxTable()
    val snippetRegistry = SnippetsRegistryImpl()
    val preferenceRegistry = PreferencesRegistryImpl()
    val shellVariablesRegistry = ShellVariablesRegistryImpl()

    val customHighlightingColors = ConcurrentHashMap<CharSequence, TextMateTextAttributesAdapter>()

    val settings = serviceAsync<TextMateUserBundlesSettings>()
    val newExtensionsMapping = ConcurrentHashMap<TextMateFileNameMatcher, CharSequence>()

    if (!builtinBundlesDisabled) {
      coroutineContext.ensureActive()

      val builtinBundlesSettings = serviceAsync<TextMateBuiltinBundlesSettings>()
      val turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames()
      val builtInBundles = withContext(Dispatchers.IO) {
        discoverBuiltinBundles(builtinBundlesSettings)
      }
      val bundlesToEnable = if (turnedOffBundleNames.isEmpty()) {
        builtInBundles
      }
      else {
        builtInBundles.filter { !turnedOffBundleNames.contains(it.name) }
      }

      registerBundlesInParallel(
        bundlesToLoad = bundlesToEnable,
        extensionMapping = newExtensionsMapping,
        customHighlightingColors = customHighlightingColors,
        interner = interner,
        syntaxTable = syntaxTable,
        preferenceRegistry = preferenceRegistry,
        snippetRegistry = snippetRegistry,
        shellVariablesRegistry = shellVariablesRegistry,
      )
    }

    val userBundles = settings.bundles
    val bundlesToLoad = getPluginBundles()
    if (!userBundles.isEmpty()) {
      bundlesToLoad.addAll(userBundles.entries.mapNotNull { entry ->
        if (entry.value.enabled) TextMateBundleToLoad(entry.value.name, entry.key) else null
      })
    }
    if (!bundlesToLoad.isEmpty()) {
      coroutineContext.ensureActive()

      registerBundlesInParallel(
        bundlesToLoad = bundlesToLoad,
        extensionMapping = newExtensionsMapping,
        customHighlightingColors =  customHighlightingColors,
        interner = interner,
        shellVariablesRegistry = shellVariablesRegistry,
        preferenceRegistry = preferenceRegistry,
        snippetRegistry = snippetRegistry,
        syntaxTable = syntaxTable,
        registrationFailed = { bundleToLoad ->
          coroutineScope.launch(Dispatchers.EDT) {
            val bundleName = bundleToLoad.name
            val errorMessage = TextMateBundle.message("textmate.cant.register.bundle", bundleName)
            Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error", bundleName),
                         errorMessage, NotificationType.ERROR)
              .addAction(NotificationAction.createSimpleExpiring(
                TextMateBundle.message("textmate.disable.bundle.notification.action",
                                       bundleName)) { settings.disableBundle(bundleToLoad.path) })
              .notify(null)
          }
        },
      )
    }

    coroutineContext.ensureActive()
    syntaxTable.compact()

    val oldExtensionsMapping = oldState?.extensionMapping
    val newState = InitializedState(
      customHighlightingColors = java.util.Map.copyOf(customHighlightingColors),
      extensionMapping = java.util.Map.copyOf(newExtensionsMapping),
      syntaxTable = syntaxTable,
      snippetRegistry = snippetRegistry,
      preferenceRegistry = preferenceRegistry,
      shellVariablesRegistry = shellVariablesRegistry,
    )
    if (fireEvents && oldExtensionsMapping != newExtensionsMapping) {
      // reloadEnabledBundles is called (fireEvents=true) ->
      // ensureInitialized is called in EDT and to execute writeAction we need EDT ->
      // deadlock.
      coroutineScope.launch {
        writeAction {
          FileTypeManagerEx.getInstanceEx().makeFileTypesChange("old mappings = $oldExtensionsMapping, new mappings$newExtensionsMapping") {
          }
        }
      }
    }
    return newState
  }

  override fun getCustomHighlightingColors(): Map<CharSequence, TextMateTextAttributesAdapter> {
    return ensureInitialized().customHighlightingColors
  }

  override fun getShellVariableRegistry(): ShellVariablesRegistry {
    return ensureInitialized().shellVariablesRegistry
  }

  override fun getSnippetRegistry(): SnippetsRegistry {
    return ensureInitialized().snippetRegistry
  }

  override fun getPreferenceRegistry(): PreferencesRegistry {
    return ensureInitialized().preferenceRegistry
  }

  override fun getLanguageDescriptorByFileName(fileName: CharSequence): TextMateLanguageDescriptor? {
    if (fileName.isEmpty()) {
      return null
    }

    val state = ensureInitialized()
    val scopeName = state.extensionMapping.get(TextMateFileNameMatcher.Name(fileName.toString().lowercase()))
    if (!scopeName.isNullOrEmpty()) {
      return TextMateLanguageDescriptor(scopeName, state.syntaxTable.getSyntax(scopeName))
    }

    val extensionsIterator = fileNameExtensions(fileName).iterator()
    while (extensionsIterator.hasNext()) {
      val descriptor = getLanguageDescriptorByExtension(extensionsIterator.next())
      if (descriptor != null) {
        return descriptor
      }
    }
    return null
  }

  override fun getLanguageDescriptorByExtension(extension: CharSequence?): TextMateLanguageDescriptor? {
    if (extension.isNullOrEmpty()) {
      return null
    }

    val state = ensureInitialized()
    val scopeName = state.extensionMapping.get(TextMateFileNameMatcher.Extension(extension.toString().lowercase()))
    return if (scopeName.isNullOrBlank()) null else TextMateLanguageDescriptor(scopeName, state.syntaxTable.getSyntax(scopeName))
  }

  override fun getFileNameMatcherToScopeNameMapping(): Map<TextMateFileNameMatcher, CharSequence> {
    return ensureInitialized().extensionMapping
  }

  override fun readBundle(directory: Path?): TextMateBundleReader? = doReadBundle(directory)

  private fun ensureInitialized(): InitializedState {
    // we set initJob in init, so, if it is null, it means that init was completed
    val job = initJob.get() ?: reinit()
    if (job.isCompleted) {
      @Suppress("OPT_IN_USAGE")
      return job.getCompleted()
    }
    else {
      if (EDT.isCurrentThreadEdt()) {
        // It should not be executed in EDT.
        SlowOperations.assertSlowOperationsAreAllowed()
        if (ApplicationManager.getApplication().isWriteAccessAllowed) {
          @Suppress("UsagesOfObsoleteApi")
          return ProgressIndicatorUtils.awaitWithCheckCanceled(job.asCompletableFuture())
        }
        else {
          // Do not localize - it is a rare situation when in production this init will be done in EDT and not yet finished.
          @Suppress("HardCodedStringLiteral")
          return runWithModalProgressBlocking(ModalTaskOwner.guess(), "Initializing TextMate bundles") {
            withTimeout(1.minutes) {
              job.await()
            }
          }
        }
      }
      else {
        return runBlockingMaybeCancellable {
          withTimeout(1.minutes) {
            job.await()
          }
        }
      }
    }
  }

  @TestOnly
  fun disableBuiltinBundles(disposable: Disposable?) {
    builtinBundlesDisabled = true
    reloadEnabledBundles()
    Disposer.register(disposable!!) {
      builtinBundlesDisabled = false
    }
  }
}

private fun doReadBundle(directory: Path?): TextMateBundleReader? {
  if (directory == null) {
    return null
  }

  val bundleType = detectBundleType(directory)
  return when (bundleType) {
    BundleType.TEXTMATE -> readTextMateBundle(directory)
    BundleType.SUBLIME -> readSublimeBundle(directory)
    BundleType.VSCODE -> readVSCBundle { relativePath ->
      try {
        return@readVSCBundle Files.newInputStream(directory.resolve(relativePath))
      }
      catch (e: NoSuchFileException) {
        LOG.warn("Cannot find referenced file `$relativePath` in bundle `$directory`")
        return@readVSCBundle null
      }
      catch (e: Throwable) {
        LOG.warn("Cannot read referenced file `$relativePath` in bundle `$directory`", e)
        return@readVSCBundle null
      }
    }
    BundleType.UNDEFINED -> null
  }
}

private suspend fun registerBundle(
  shellVariablesRegistry: ShellVariablesRegistryImpl,
  syntaxTable: TextMateSyntaxTable,
  preferenceRegistry: PreferencesRegistryImpl,
  snippetRegistry: SnippetsRegistryImpl,
  directory: Path?,
  extensionMapping: MutableMap<TextMateFileNameMatcher, CharSequence>,
  customHighlightingColors: MutableMap<CharSequence, TextMateTextAttributesAdapter>,
  interner: Interner<CharSequence>,
): Boolean {
  val reader = withContext(Dispatchers.IO) {
    doReadBundle(directory)
  } ?: return false
  registerLanguageSupport(syntaxTable = syntaxTable, reader = reader, extensionMapping = extensionMapping, interner = interner)
  registerPreferences(reader = reader, preferenceRegistry = preferenceRegistry, customHighlightingColors = customHighlightingColors, interner = interner, shellVariablesRegistry = shellVariablesRegistry)
  registerSnippets(reader = reader, snippetRegistry = snippetRegistry)
  return true
}

 private fun registerLanguageSupport(
   syntaxTable: TextMateSyntaxTable,
   reader: TextMateBundleReader,
   extensionMapping: MutableMap<TextMateFileNameMatcher, CharSequence>,
   interner: Interner<CharSequence>,
 ) {
   val grammarIterator = reader.readGrammars().iterator()
   while (grammarIterator.hasNext()) {
     val grammar = grammarIterator.next()
     val rootScopeName = syntaxTable.loadSyntax(grammar.plist.value, interner) ?: continue
     for (fileNameMatcher in grammar.fileNameMatchers) {
       if (fileNameMatcher is TextMateFileNameMatcher.Name) {
         val newName = fileNameMatcher.fileName.lowercase()
         extensionMapping.put(fileNameMatcher.copy(newName), rootScopeName)
       }
       else {
         extensionMapping.put(fileNameMatcher, rootScopeName)
       }
     }
   }
 }

private fun registerPreferences(
  reader: TextMateBundleReader,
  shellVariablesRegistry: ShellVariablesRegistryImpl,
  preferenceRegistry: PreferencesRegistryImpl,
  customHighlightingColors: MutableMap<CharSequence, TextMateTextAttributesAdapter>,
  interner: Interner<CharSequence>
) {
  val preferencesIterator = reader.readPreferences().iterator()
  while (preferencesIterator.hasNext()) {
    val preferences = preferencesIterator.next()
    val scopeName = interner.intern(preferences.scopeName)
    val internedHighlightingPairs = preferences.highlightingPairs?.let { pairs ->
      if (pairs.isEmpty()) {
        emptySet()
      }
      else {
        pairs.mapTo(HashSet(pairs.size)) { TextMateBracePair(interner.intern(it.left), interner.intern(it.right)) }
      }
    }
    val internedSmartTypingPairs = preferences.smartTypingPairs?.let { pairs ->
      if (pairs.isEmpty()) {
        emptySet()
      }
      else {
        pairs.mapTo(HashSet(pairs.size)) { TextMateAutoClosingPair(interner.intern(it.left), interner.intern(it.right), it.notIn) }
      }
    }
    val internedSurroundingPairs = preferences.surroundingPairs?.let { pairs ->
      if (pairs.isEmpty()) {
        emptySet()
      }
      else {
        pairs.mapTo(HashSet(pairs.size)) { TextMateBracePair(interner.intern(it.left), interner.intern(it.right)) }
      }
    }
    preferenceRegistry.addPreferences(Preferences(
      scopeName,
      internedHighlightingPairs,
      internedSmartTypingPairs,
      internedSurroundingPairs,
      preferences.autoCloseBefore,
      preferences.indentationRules,
      preferences.onEnterRules,
    ))
    for (variable in preferences.variables) {
      shellVariablesRegistry.addVariable(variable)
    }
    val customHighlightingAttributes = preferences.customHighlightingAttributes
    if (customHighlightingAttributes != null) {
      customHighlightingColors.put(scopeName, TextMateTextAttributesAdapter(scopeName, customHighlightingAttributes))
    }
  }
}

private fun registerSnippets(reader: TextMateBundleReader, snippetRegistry: SnippetsRegistryImpl) {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    // it's used in internal mode only (see org.jetbrains.plugins.textmate.editor.TextMateCustomLiveTemplate.isApplicable),
    // do not register to save some memory and loading time
    val snippetsIterator = reader.readSnippets().iterator()
    while (snippetsIterator.hasNext()) {
      snippetRegistry.register(snippetsIterator.next())
    }
  }
}

private val bundledBundlePath: Path
  get() = PluginPathManager.getPluginHome("textmate").toPath().resolve("lib/bundles").normalize()

internal fun discoverBuiltinBundles(builtinBundlesSettings: TextMateBuiltinBundlesSettings): List<TextMateBundleToLoad> {
  val builtinBundles = builtinBundlesSettings.builtinBundles
  if (builtinBundles.isNotEmpty()) {
    return builtinBundles
  }

  val builtinBundlesPath = bundledBundlePath
  try {
    Files.list(builtinBundlesPath).use { files ->
      val bundles = files
        .filter { file -> !file.fileName.toString().startsWith('.') }
        .map { file -> TextMateBundleToLoad(file.fileName.toString(), file.toString()) }
        .toList()
      builtinBundlesSettings.builtinBundles = bundles
      return bundles
    }
  }
  catch (e: Throwable) {
    LOG.warn("Couldn't list builtin textmate bundles at $builtinBundlesPath", e)
    return emptyList()
  }
}

private suspend fun registerBundlesInParallel(
  bundlesToLoad: List<TextMateBundleToLoad>,
  extensionMapping: MutableMap<TextMateFileNameMatcher, CharSequence>,
  customHighlightingColors: MutableMap<CharSequence, TextMateTextAttributesAdapter>,
  interner: Interner<CharSequence>,
  preferenceRegistry: PreferencesRegistryImpl,
  shellVariablesRegistry: ShellVariablesRegistryImpl,
  snippetRegistry: SnippetsRegistryImpl,
  syntaxTable: TextMateSyntaxTable,
  registrationFailed: ((TextMateBundleToLoad) -> Unit)? = null,
) {
  coroutineScope {
    fun handleError(bundleToLoad: TextMateBundleToLoad, t: Throwable? = null) {
      if (registrationFailed == null || ApplicationManager.getApplication().isHeadlessEnvironment) {
        LOG.error("Cannot load builtin textmate bundle", t, bundleToLoad.toString())
      }
      else {
        registrationFailed(bundleToLoad)
      }
    }

    for (bundleToLoad in bundlesToLoad) {
      coroutineContext.ensureActive()
      launch {
        val registered = try {
          registerBundle(
            shellVariablesRegistry = shellVariablesRegistry,
            directory = Path.of(bundleToLoad.path),
            extensionMapping = extensionMapping,
            customHighlightingColors = customHighlightingColors,
            preferenceRegistry = preferenceRegistry,
            snippetRegistry = snippetRegistry,
            syntaxTable = syntaxTable,
            interner = interner,
          )
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          handleError(bundleToLoad, e)
          null
        }

        if (registered != null && !registered) {
          handleError(bundleToLoad)
        }
      }
    }
  }
}

internal data class TextMateBundleToLoad(@JvmField val name: String, @JvmField val path: String)

private class ConcurrentInterner<T : Any> : Interner<T>() {
  private val map = ConcurrentHashMap<T, T>(14_000, 0.75f, 2)

  override fun intern(name: T): T {
    return map.putIfAbsent(name, name) ?: name
  }

  override fun clear() {
    map.clear()
  }

  override fun getValues(): Set<T> = java.util.HashSet(map.values)
}

private fun getPluginBundles(): MutableList<TextMateBundleToLoad> {
  val bundleProviders = TextMateBundleProvider.EP_NAME.extensionList
  val pluginBundles = mutableListOf<TextMateBundleProvider.PluginBundle>()
  for (provider in bundleProviders) {
    try {
      pluginBundles.addAll(provider.getBundles())
    }
    catch (e: Exception) {
      logger<TextMateServiceImpl>().error("${provider} failed", e)
    }
  }
  return pluginBundles.distinctBy { it.path }.mapTo(mutableListOf()) { TextMateBundleToLoad(it.name, it.path.absolutePathString()) }
}