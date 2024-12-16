@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.plugins.textmate

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.textmate.TextMateService.LOG
import org.jetbrains.plugins.textmate.bundles.*
import org.jetbrains.plugins.textmate.bundles.BundleType.Companion.detectBundleType
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings
import org.jetbrains.plugins.textmate.editor.fileNameExtensions
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.TextMateInterner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.preferences.*
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableCore
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter
import org.jetbrains.plugins.textmate.language.syntax.lexer.SyntaxMatchUtils
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile

class TextMateServiceImpl(private val myScope: CoroutineScope) : TextMateService() {
  private var builtinBundlesDisabled = false

  @Volatile
  private var isInitialized = false
  private val registrationLock = ReentrantLock()

  private val customHighlightingColors = HashMap<CharSequence, TextMateTextAttributesAdapter>()
  private var extensionMapping: Map<TextMateFileNameMatcher, CharSequence> = java.util.Map.of()
  private val syntaxTable = TextMateSyntaxTableCore()
  private val snippetRegistry = SnippetsRegistryImpl()
  private val preferenceRegistry = PreferencesRegistryImpl()
  private val shellVariablesRegistry = ShellVariablesRegistryImpl()
  private val interner: TextMateInterner = TextMateConcurrentMapInterner()

  init {
    val app = ApplicationManager.getApplication()
    val checkCancelled = if (app == null || app.isUnitTestMode) null else Runnable { ProgressManager.checkCanceled() }
    SyntaxMatchUtils.setCheckCancelledCallback(checkCancelled)
  }

  override fun reloadEnabledBundles() {
    registerBundles(fireEvents = true)
  }

  @OptIn(IntellijInternalApi::class)
  private fun registerBundles(fireEvents: Boolean) {
    registrationLock.lock()
    try {
      val oldExtensionsMapping = extensionMapping
      unregisterAllBundles()

      val settings = TextMateUserBundlesSettings.getInstance() ?: return
      val newExtensionsMapping = ConcurrentHashMap<TextMateFileNameMatcher, CharSequence>()

      if (!builtinBundlesDisabled) {
        val builtinBundlesSettings = TextMateBuiltinBundlesSettings.instance
        if (builtinBundlesSettings != null) {
          val turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames()
          val builtInBundles = discoverBuiltinBundles(builtinBundlesSettings)
          val bundlesToEnable = if (turnedOffBundleNames.isEmpty()) {
            builtInBundles
          }
          else {
            builtInBundles.filter { !turnedOffBundleNames.contains(it.name) }
          }
          registerBundlesInParallel(
            scope = myScope,
            bundlesToLoad = bundlesToEnable,
            registrar = { registerBundle(Path.of(it.path), newExtensionsMapping) },
          )
        }
      }

      val userBundles = settings.bundles
      val bundlesToLoad = this.getPluginBundles()
      if (!userBundles.isEmpty()) {
        bundlesToLoad.addAll(userBundles.entries.mapNotNull { entry ->
          if (entry.value.enabled) TextMateBundleToLoad(entry.value.name, entry.key) else null
        })
      }
      if (!bundlesToLoad.isEmpty()) {
        registerBundlesInParallel(
          scope = myScope,
          bundlesToLoad = bundlesToLoad,
          registrar = { bundleToLoad -> registerBundle(Path.of(bundleToLoad.path), newExtensionsMapping) },
          registrationFailed = { bundleToLoad ->
            val bundleName = bundleToLoad.name
            val errorMessage = TextMateBundle.message("textmate.cant.register.bundle", bundleName)
            Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error", bundleName),
                         errorMessage, NotificationType.ERROR)
              .addAction(NotificationAction.createSimpleExpiring(
                TextMateBundle.message("textmate.disable.bundle.notification.action",
                                       bundleName)) { settings.disableBundle(bundleToLoad.path) })
              .notify(null)
          },
        )
      }

      if (fireEvents && oldExtensionsMapping != newExtensionsMapping) {
        fireFileTypesChangedEvent("old mappings = $oldExtensionsMapping, new mappings$newExtensionsMapping") {
          extensionMapping = java.util.Map.copyOf(newExtensionsMapping)
        }
      }
      else {
        extensionMapping = java.util.Map.copyOf(newExtensionsMapping)
      }
      syntaxTable.compact()
    }
    finally {
      registrationLock.unlock()
    }
  }

  private fun unregisterAllBundles() {
    extensionMapping = java.util.Map.of()
    preferenceRegistry.clear()
    customHighlightingColors.clear()
    syntaxTable.clear()
    snippetRegistry.clear()
    shellVariablesRegistry.clear()
    interner.clear()
  }

  override fun getCustomHighlightingColors(): Map<CharSequence, TextMateTextAttributesAdapter> {
    ensureInitialized()
    return customHighlightingColors
  }

  override fun getShellVariableRegistry(): ShellVariablesRegistry {
    ensureInitialized()
    return shellVariablesRegistry
  }

  override fun getSnippetRegistry(): SnippetsRegistry {
    ensureInitialized()
    return snippetRegistry
  }

  override fun getPreferenceRegistry(): PreferencesRegistry {
    ensureInitialized()
    return preferenceRegistry
  }

  override fun getLanguageDescriptorByFileName(fileName: CharSequence): TextMateLanguageDescriptor? {
    if (Strings.isEmpty(fileName)) {
      return null
    }

    ensureInitialized()
    val scopeName = extensionMapping.get(TextMateFileNameMatcher.Name(fileName.toString().lowercase()))
    if (!scopeName.isNullOrEmpty()) {
      return TextMateLanguageDescriptor(scopeName, syntaxTable.getSyntax(scopeName))
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
    if (Strings.isEmpty(extension)) {
      return null
    }

    ensureInitialized()
    val scopeName = extensionMapping.get(TextMateFileNameMatcher.Extension(StringUtil.toLowerCase(extension.toString())))
    return if (scopeName.isNullOrBlank()) null else TextMateLanguageDescriptor(scopeName, syntaxTable.getSyntax(scopeName))
  }

  override fun getFileNameMatcherToScopeNameMapping(): Map<TextMateFileNameMatcher, CharSequence> {
    ensureInitialized()
    return extensionMapping
  }

  override fun readBundle(directory: Path?): TextMateBundleReader? {
    if (directory != null) {
      val bundleType = detectBundleType(directory)
      return when (bundleType) {
        BundleType.TEXTMATE -> readTextMateBundle(directory)
        BundleType.SUBLIME -> readSublimeBundle(directory)
        BundleType.VSCODE -> readVSCBundle { relativePath: String ->
          try {
            return@readVSCBundle Files.newInputStream(directory.resolve(relativePath))
          }
          catch (_: NoSuchFileException) {
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
    return null
  }

  private fun ensureInitialized() {
    if (!isInitialized) {
      registrationLock.lock()
      try {
        if (isInitialized) return
        registerBundles(fireEvents = false)
        isInitialized = true
      }
      finally {
        registrationLock.unlock()
      }
    }
  }

  private fun registerBundle(directory: Path?, extensionMapping: MutableMap<TextMateFileNameMatcher, CharSequence>): Boolean {
    val reader = readBundle(directory)
    if (reader != null) {
      registerLanguageSupport(reader, extensionMapping)
      registerPreferences(reader)
      registerSnippets(reader)
      return true
    }
    return false
  }

  private fun registerSnippets(reader: TextMateBundleReader) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // it's used in internal mode only (see org.jetbrains.plugins.textmate.editor.TextMateCustomLiveTemplate.isApplicable),
      // do not register to save some memory and loading time
      val snippetsIterator = reader.readSnippets().iterator()
      while (snippetsIterator.hasNext()) {
        snippetRegistry.register(snippetsIterator.next())
      }
    }
  }

  private fun registerPreferences(reader: TextMateBundleReader) {
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
          pairs.mapTo(HashSet(pairs.size)) { TextMateAutoClosingPair(interner.intern(it.left.toString()), interner.intern(it.right.toString()), it.notIn) }
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
      preferenceRegistry.addPreferences(Preferences(scopeName,
                                                    internedHighlightingPairs,
                                                    internedSmartTypingPairs,
                                                    internedSurroundingPairs,
                                                    preferences.autoCloseBefore,
                                                    preferences.indentationRules,
                                                    preferences.onEnterRules))
      for (variable in preferences.variables) {
        shellVariablesRegistry.addVariable(variable)
      }
      val customHighlightingAttributes = preferences.customHighlightingAttributes
      if (customHighlightingAttributes != null) {
        customHighlightingColors.put(scopeName, TextMateTextAttributesAdapter(scopeName, customHighlightingAttributes))
      }
    }
  }

  private fun registerLanguageSupport(reader: TextMateBundleReader, extensionMapping: MutableMap<TextMateFileNameMatcher, CharSequence>) {
    val grammarIterator = reader.readGrammars().iterator()
    while (grammarIterator.hasNext()) {
      val grammar = grammarIterator.next()
      val rootScopeName = syntaxTable.addSyntax(grammar.plist.value, interner) ?: continue
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

  @TestOnly
  fun disableBuiltinBundles(disposable: Disposable?) {
    builtinBundlesDisabled = true
    reloadEnabledBundles()
    isInitialized = true
    Disposer.register(disposable!!) {
      builtinBundlesDisabled = false
      unregisterAllBundles()
      isInitialized = false
    }
  }
}

private val bundledBundlePath: Path
  get() = PluginPathManager.getPluginHome("textmate").toPath().resolve("lib/bundles").normalize()

private fun fireFileTypesChangedEvent(reason: @NonNls String, update: Runnable) {
  ApplicationManager.getApplication().invokeLater(
    {
      ApplicationManager.getApplication().runWriteAction {
        val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
        fileTypeManager.makeFileTypesChange(reason, update)
      }
    }, ModalityState.nonModal())
}

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
