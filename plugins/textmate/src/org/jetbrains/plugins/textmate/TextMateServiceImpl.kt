@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package org.jetbrains.plugins.textmate

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.textmate.bundles.BUNDLES_INDEX_FILE_NAME
import org.jetbrains.plugins.textmate.bundles.TextMateBundleIndexEntry
import org.jetbrains.plugins.textmate.bundles.TextMateBundleReader
import org.jetbrains.plugins.textmate.bundles.TextMateBundlesIndex
import org.jetbrains.plugins.textmate.bundles.TextMateFileNameMatcher
import org.jetbrains.plugins.textmate.bundles.TextMateGrammar
import org.jetbrains.plugins.textmate.bundles.TextMatePreferences
import org.jetbrains.plugins.textmate.bundles.bundleIndexEntry
import org.jetbrains.plugins.textmate.bundles.readBundleDirectory
import org.jetbrains.plugins.textmate.configuration.TextMateBuiltinBundlesSettings
import org.jetbrains.plugins.textmate.configuration.TextMateUserBundlesSettings
import org.jetbrains.plugins.textmate.editor.fileNameExtensions
import org.jetbrains.plugins.textmate.language.TextMateConcurrentMapInterner
import org.jetbrains.plugins.textmate.language.TextMateInterner
import org.jetbrains.plugins.textmate.language.TextMateLanguageDescriptor
import org.jetbrains.plugins.textmate.language.preferences.Preferences
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistry
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistryBuilder
import org.jetbrains.plugins.textmate.language.preferences.PreferencesRegistryImpl
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistry
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistryBuilder
import org.jetbrains.plugins.textmate.language.preferences.ShellVariablesRegistryImpl
import org.jetbrains.plugins.textmate.language.preferences.SnippetsRegistry
import org.jetbrains.plugins.textmate.language.preferences.SnippetsRegistryBuilder
import org.jetbrains.plugins.textmate.language.preferences.SnippetsRegistryImpl
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableBuilder
import org.jetbrains.plugins.textmate.language.syntax.TextMateSyntaxTableCore
import org.jetbrains.plugins.textmate.language.syntax.highlighting.TextMateTextAttributesAdapter
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigher
import org.jetbrains.plugins.textmate.language.syntax.selector.TextMateSelectorWeigherImpl
import org.jetbrains.plugins.textmate.language.syntax.selector.caching
import org.jetbrains.plugins.textmate.plist.JsonOrXmlOrYamlPlistReader
import org.jetbrains.plugins.textmate.plist.XmlPlistReader
import org.jetbrains.plugins.textmate.plist.YamlPlistReader
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.Volatile
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.time.measureTimedValue

/**
 * Registers the enabled bundles from their metadata: the built-in bundles from the index of the bundles directory,
 * the user and plugin bundles from their grammar lists. A bundle loads on the first request of one of its grammars,
 * together with the bundles that the grammar includes.
 */
class TextMateServiceImpl(private val myScope: CoroutineScope) : TextMateService() {
  private var builtinBundlesDisabled = false

  @Volatile
  private var isInitialized = false
  private val registrationLock = ReentrantLock()
  private val initializationJob = AtomicReference<Job?>(null)

  private val globalCachingSelectorWeigher = TextMateSelectorWeigherImpl().caching()
  private val interner: TextMateInterner = TextMateConcurrentMapInterner()

  @Volatile
  private var registry = BundleRegistry(emptyList(), globalCachingSelectorWeigher, interner)

  override fun reloadEnabledBundles() {
    registerBundles(fireEvents = true)
  }

  private fun registerBundles(fireEvents: Boolean) {
    registrationLock.lock()
    try {
      val oldMapping = registry.fileNameMapping
      unregisterAllBundles()

      val settings = TextMateUserBundlesSettings.getInstance() ?: return
      val bundles = ArrayList<RegisteredBundle>()
      if (!builtinBundlesDisabled) {
        TextMateBuiltinBundlesSettings.instance?.let { bundles.addAll(registerBuiltinBundles(it)) }
      }

      val bundlesToLoad = ArrayList<TextMateBundleToLoad>()
      for (pluginBundle in getPluginBundles()) {
        if (pluginBundle.extensions.isEmpty()) {
          bundlesToLoad.add(pluginBundle.bundle)
        }
        else {
          bundles.add(RegisteredBundle.Declared(pluginBundle.bundle, pluginBundle.extensions))
        }
      }
      for ((path, bundle) in settings.bundles) {
        if (bundle.enabled) {
          bundlesToLoad.add(TextMateBundleToLoad(bundle.name, path))
        }
      }
      if (bundlesToLoad.isNotEmpty()) {
        val registered = ConcurrentHashMap<TextMateBundleToLoad, RegisteredBundle>()
        registerBundlesInParallel(
          scope = myScope,
          bundlesToLoad = bundlesToLoad,
          registrar = { bundleToLoad ->
            val bundle = readExternalBundle(bundleToLoad)
            if (bundle != null) {
              registered.put(bundleToLoad, bundle)
            }
            bundle != null
          },
          registrationFailed = { bundleToLoad -> notifyBundleLoadFailure(settings, bundleToLoad) },
        )
        bundlesToLoad.mapNotNullTo(bundles) { registered.get(it) }
      }

      val newRegistry = BundleRegistry(bundles, globalCachingSelectorWeigher, interner)
      registry = newRegistry
      if (fireEvents && oldMapping != newRegistry.fileNameMapping) {
        fireFileTypesChangedEvent("TextMate bundles were reloaded, ${oldMapping.size} -> ${newRegistry.fileNameMapping.size} file name matchers")
      }
    }
    finally {
      registrationLock.unlock()
    }
  }

  private fun registerBuiltinBundles(builtinBundlesSettings: TextMateBuiltinBundlesSettings): List<RegisteredBundle> {
    val turnedOffBundleNames = builtinBundlesSettings.getTurnedOffBundleNames()
    val bundlesPath = bundledBundlePath
    val index = readBuiltinBundlesIndex(bundlesPath)
    builtinBundlesSettings.builtinBundles = index.bundles.map { builtinBundleToLoad(bundlesPath, it) }
    return index.bundles.mapNotNull { entry ->
      if (turnedOffBundleNames.contains(entry.name)) {
        null
      }
      else {
        RegisteredBundle.Builtin(builtinBundleToLoad(bundlesPath, entry), entry)
      }
    }
  }

  private fun readExternalBundle(bundleToLoad: TextMateBundleToLoad): RegisteredBundle.External? {
    val reader = readBundle(Path.of(bundleToLoad.path)) ?: return null
    return RegisteredBundle.External(bundleToLoad, reader, reader.readGrammars().toList(), reader.readPreferences().toList())
  }

  private fun notifyBundleLoadFailure(settings: TextMateUserBundlesSettings, bundleToLoad: TextMateBundleToLoad) {
    val bundleName = bundleToLoad.name
    val errorMessage = TextMateBundle.message("textmate.cant.register.bundle", bundleName)
    Notification("TextMate Bundles", TextMateBundle.message("textmate.bundle.load.error", bundleName),
                 errorMessage, NotificationType.ERROR)
      .addAction(NotificationAction.createSimpleExpiring(
        TextMateBundle.message("textmate.disable.bundle.notification.action",
                               bundleName)) { settings.disableBundle(bundleToLoad.path) })
      .notify(null)
  }

  private fun unregisterAllBundles() {
    registry = BundleRegistry(emptyList(), globalCachingSelectorWeigher, interner)
    globalCachingSelectorWeigher.clearCache()
    interner.clear()
  }

  override fun getCustomHighlightingColors(): Map<CharSequence, TextMateTextAttributesAdapter> {
    ensureInitialized()
    return registry.customHighlightingColors
  }

  override fun getShellVariableRegistry(): ShellVariablesRegistry {
    ensureInitialized()
    return registry.shellVariablesRegistry
  }

  override fun getSnippetRegistry(): SnippetsRegistry {
    ensureInitialized()
    return registry.snippetRegistry
  }

  override fun getPreferenceRegistry(): PreferencesRegistry {
    ensureInitialized()
    return registry.preferenceRegistry
  }

  override fun hasLanguageForFileName(fileName: CharSequence): Boolean {
    if (Strings.isEmpty(fileName)) {
      return false
    }

    ensureInitialized()
    return registry.matchFileName(fileName) != null
  }

  override fun getLanguageDescriptorByFileName(fileName: CharSequence): TextMateLanguageDescriptor? {
    if (Strings.isEmpty(fileName)) {
      return null
    }

    ensureInitialized()
    val scopeName = resolveScopeName { it.matchFileName(fileName) } ?: return null
    return getLanguageDescriptor(scopeName)
  }

  override fun getLanguageDescriptorByExtension(extension: CharSequence?): TextMateLanguageDescriptor? {
    if (extension.isNullOrEmpty()) {
      return null
    }

    ensureInitialized()
    val scopeName = resolveScopeName { it.matchExtension(extension) } ?: return null
    return getLanguageDescriptor(scopeName)
  }

  override fun getFileNameMatcherToScopeNameMapping(): Map<TextMateFileNameMatcher, CharSequence> {
    ensureInitialized()
    return registry.fileNameMapping
  }

  /**
   * Resolves a file name or an extension to a scope name.
   * A match of a declared plugin bundle loads the bundle first, because the scope of such a bundle is known after the load only.
   */
  private fun resolveScopeName(match: (BundleRegistry) -> FileNameMatch?): CharSequence? {
    while (true) {
      val registry = this.registry
      when (val result = match(registry)) {
        null -> return null
        is FileNameMatch.Scope -> return result.scopeName
        is FileNameMatch.UnloadedBundle -> {
          ProgressIndicatorUtils.awaitWithCheckCanceled(startBundleLoadJob(registry, result.bundle).asCompletableFuture())
        }
      }
    }
  }

  /**
   * Loads the bundle that provides [scopeName] when the bundle is not loaded yet.
   */
  private fun getLanguageDescriptor(scopeName: CharSequence): TextMateLanguageDescriptor {
    while (true) {
      val registry = this.registry
      val bundle = registry.bundlesByScopeName.get(scopeName.toString())
      if (bundle == null || bundle.loaded) {
        return registry.syntaxTable.getLanguageDescriptor(scopeName)
      }
      ProgressIndicatorUtils.awaitWithCheckCanceled(startBundleLoadJob(registry, bundle).asCompletableFuture())
      // when the bundles were registered again during the load, the bundle of the new registration loads on the next pass
    }
  }

  override fun readBundle(directory: Path?): TextMateBundleReader? {
    if (directory == null) {
      return null
    }
    val plistReader = JsonOrXmlOrYamlPlistReader(xmlReader = XmlPlistReader(), yamlReader = YamlPlistReader())
    return readBundleDirectory(directory, plistReader)
  }

  override fun startInitialization() {
    if (!isInitialized) {
      startInitializationJob()
    }
  }

  private fun ensureInitialized() {
    if (!isInitialized) {
      val job = startInitializationJob()
      ProgressIndicatorUtils.awaitWithCheckCanceled(job.asCompletableFuture())
    }
  }

  private fun startInitializationJob(): Job {
    while (true) {
      initializationJob.get()?.let { return it }
      val job = myScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        registrationLock.lock()
        try {
          if (!isInitialized) {
            registerBundles(fireEvents = false)
            isInitialized = true
          }
        }
        finally {
          registrationLock.unlock()
        }
      }
      if (initializationJob.compareAndSet(null, job)) {
        job.invokeOnCompletion { cause ->
          if (cause != null && !isInitialized) {
            LOG.debug("Initialization of textmate bundles was cancelled", cause)
            initializationJob.compareAndSet(job, null)
          }
        }
        job.start()
        return job
      }
      job.cancel()
    }
  }

  private fun startBundleLoadJob(registry: BundleRegistry, bundle: RegisteredBundle): Job {
    while (true) {
      bundle.loadJob.get()?.let { return it }
      val job = myScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        registrationLock.lock()
        try {
          if (!bundle.loaded && this@TextMateServiceImpl.registry === registry) {
            loadBundleWithIncludes(registry, bundle)
          }
        }
        finally {
          registrationLock.unlock()
        }
      }
      if (bundle.loadJob.compareAndSet(null, job)) {
        job.invokeOnCompletion { cause ->
          if (cause != null && !bundle.loaded) {
            LOG.debug("Loading of textmate bundle ${bundle.bundleToLoad} was cancelled", cause)
            bundle.loadJob.compareAndSet(job, null)
          }
        }
        job.start()
        return job
      }
      job.cancel()
    }
  }

  /**
   * Loads [bundle] and every registered bundle that provides a scope the loaded grammars include, then rebuilds the tables.
   */
  private fun loadBundleWithIncludes(registry: BundleRegistry, bundle: RegisteredBundle) {
    loadBundle(registry, bundle)
    while (true) {
      val providers = registry.unloadedIncludedBundles()
      if (providers.isEmpty()) {
        break
      }
      for (provider in providers) {
        loadBundle(registry, provider)
      }
    }
    registry.rebuildTables()
  }

  private fun loadBundle(registry: BundleRegistry, bundle: RegisteredBundle) {
    val bundleToLoad = bundle.bundleToLoad
    var error: Throwable? = null
    val (loaded, duration) = measureTimedValue {
      try {
        addBundle(registry, bundle)
      }
      catch (e: Throwable) {
        rethrowControlFlowException(e)
        error = e
        false
      }
    }
    bundle.loaded = true
    if (loaded) {
      LOG.debug("Bundle `${bundleToLoad.path}` loaded in $duration")
      return
    }
    val settings = TextMateUserBundlesSettings.getInstance()
    when (bundle) {
      is RegisteredBundle.Builtin -> LOG.error("Cannot load builtin textmate bundle", error, bundleToLoad.toString())
      is RegisteredBundle.External, is RegisteredBundle.Declared -> {
        if (settings == null || ApplicationManager.getApplication().isHeadlessEnvironment) {
          LOG.error("Cannot load textmate bundle", error, bundleToLoad.toString())
        }
        else {
          LOG.warn("Cannot load textmate bundle $bundleToLoad", error)
          myScope.launch(Dispatchers.EDT) {
            notifyBundleLoadFailure(settings, bundleToLoad)
          }
        }
      }
    }
  }

  private fun addBundle(registry: BundleRegistry, bundle: RegisteredBundle): Boolean {
    return when (bundle) {
      is RegisteredBundle.Builtin -> {
        val reader = readBundle(Path.of(bundle.bundleToLoad.path)) ?: return false
        registry.addGrammars(reader.readGrammars().asIterable())
        registry.addPreferences(reader.readPreferences().asIterable())
        registerSnippets(reader, registry.snippetsRegistryBuilder)
        true
      }
      is RegisteredBundle.External -> {
        registry.addGrammars(bundle.grammars)
        registerSnippets(bundle.reader, registry.snippetsRegistryBuilder)
        true
      }
      is RegisteredBundle.Declared -> {
        val reader = readBundle(Path.of(bundle.bundleToLoad.path)) ?: return false
        val grammars = registry.addGrammars(reader.readGrammars().asIterable())
        registry.addPreferences(reader.readPreferences().asIterable())
        registerSnippets(reader, registry.snippetsRegistryBuilder)
        bundle.listedExtensionScopes = listedExtensionScopes(bundle.extensions, grammars)
        bundle.firstScopeName = grammars.firstOrNull()?.second
        true
      }
    }
  }

  private fun registerSnippets(reader: TextMateBundleReader, snippetRegistryBuilder: SnippetsRegistryBuilder) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      // it's used in internal mode only (see org.jetbrains.plugins.textmate.editor.TextMateCustomLiveTemplate.isApplicable),
      // do not register to save some memory and loading time
      val snippetsIterator = reader.readSnippets().iterator()
      while (snippetsIterator.hasNext()) {
        snippetRegistryBuilder.register(snippetsIterator.next())
      }
    }
  }

  @TestOnly
  fun loadedBundleNames(): Set<String> {
    return registry.bundles.filter { it.loaded }.mapTo(HashSet()) { it.bundleToLoad.name }
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

/**
 * The enabled bundles of one registration, their file name mapping, and the tables that the loaded bundles fill.
 */
private class BundleRegistry(
  val bundles: List<RegisteredBundle>,
  weigher: TextMateSelectorWeigher,
  private val interner: TextMateInterner,
) {
  private val syntaxTableBuilder = TextMateSyntaxTableBuilder(interner)
  private val preferencesBuilder = PreferencesRegistryBuilder(weigher)
  private val shellVariablesRegistryBuilder = ShellVariablesRegistryBuilder(weigher)
  private val customHighlightingColorsBuilder = HashMap<CharSequence, TextMateTextAttributesAdapter>()
  val snippetsRegistryBuilder = SnippetsRegistryBuilder(weigher)

  private val baseMapping: Map<TextMateFileNameMatcher, CharSequence>
  private val declaredBundles: List<RegisteredBundle.Declared>
  private val declaredBundlesByExtension: Map<String, List<RegisteredBundle.Declared>>
  val bundlesByScopeName: Map<String, RegisteredBundle>

  /**
   * The matchers of the registered bundles, plus the declared extensions of the loaded plugin bundles with their scopes.
   */
  @Volatile
  var fileNameMapping: Map<TextMateFileNameMatcher, CharSequence> = emptyMap()
    private set

  @Volatile
  var syntaxTable: TextMateSyntaxTableCore = TextMateSyntaxTableCore(emptyMap())
    private set

  @Volatile
  var preferenceRegistry: PreferencesRegistry = PreferencesRegistryImpl(weigher)
    private set

  @Volatile
  var snippetRegistry: SnippetsRegistry = SnippetsRegistryImpl(weigher, emptyMap())
    private set

  @Volatile
  var shellVariablesRegistry: ShellVariablesRegistry = ShellVariablesRegistryImpl(weigher, emptyMap())
    private set

  @Volatile
  var customHighlightingColors: Map<CharSequence, TextMateTextAttributesAdapter> = emptyMap()
    private set

  init {
    val mapping = HashMap<TextMateFileNameMatcher, CharSequence>()
    val bundlesByScopeName = HashMap<String, RegisteredBundle>()
    val declaredBundles = ArrayList<RegisteredBundle.Declared>()
    val declaredBundlesByExtension = HashMap<String, MutableList<RegisteredBundle.Declared>>()

    fun addEntry(bundle: RegisteredBundle, entry: TextMateBundleIndexEntry) {
      for (grammar in entry.grammars) {
        val scopeName = interner.intern(grammar.scopeName)
        bundlesByScopeName.put(scopeName, bundle)
        for (fileNameMatcher in grammar.fileNameMatchers()) {
          val key = when (fileNameMatcher) {
            is TextMateFileNameMatcher.Name -> fileNameMatcher.copy(fileName = fileNameMatcher.fileName.lowercase())
            is TextMateFileNameMatcher.Extension, is TextMateFileNameMatcher.Pattern -> fileNameMatcher
          }
          mapping.put(key, scopeName)
        }
      }
    }

    for (bundle in bundles) {
      when (bundle) {
        is RegisteredBundle.Builtin -> addEntry(bundle, bundle.entry)
        is RegisteredBundle.External -> {
          addEntry(bundle, bundle.entry)
          // a classic preference can apply to a scope of another bundle, so it registers before any bundle loads
          addPreferences(bundle.preferences)
        }
        is RegisteredBundle.Declared -> {
          declaredBundles.add(bundle)
          for (extension in bundle.extensions) {
            declaredBundlesByExtension.getOrPut(extension) { ArrayList() }.add(bundle)
          }
        }
      }
    }
    baseMapping = java.util.Map.copyOf(mapping)
    this.declaredBundles = declaredBundles
    this.declaredBundlesByExtension = declaredBundlesByExtension
    this.bundlesByScopeName = bundlesByScopeName
    rebuildTables()
  }

  fun matchFileName(fileName: CharSequence): FileNameMatch? {
    val scopeName = fileNameMapping.get(TextMateFileNameMatcher.Name(fileName.toString().lowercase()))
    if (!scopeName.isNullOrEmpty()) {
      return FileNameMatch.Scope(scopeName)
    }

    val extensionsIterator = fileNameExtensions(fileName).iterator()
    while (extensionsIterator.hasNext()) {
      matchExtension(extensionsIterator.next())?.let { return it }
    }
    return null
  }

  /**
   * A declared extension of a plugin bundle wins over the same extension of another bundle.
   */
  fun matchExtension(extension: CharSequence): FileNameMatch? {
    val key = StringUtil.toLowerCase(extension.toString())
    declaredBundlesByExtension.get(key)?.firstOrNull { !it.loaded }?.let { return FileNameMatch.UnloadedBundle(it) }
    val scopeName = fileNameMapping.get(TextMateFileNameMatcher.Extension(key))
    return if (scopeName.isNullOrBlank()) null else FileNameMatch.Scope(scopeName)
  }

  /**
   * Adds the grammars to the syntax table and returns the root scope name of every grammar that has one.
   */
  fun addGrammars(grammars: Iterable<TextMateGrammar>): List<Pair<TextMateGrammar, CharSequence>> {
    return grammars.mapNotNull { grammar ->
      syntaxTableBuilder.addSyntax(grammar.plist.value)?.let { grammar to it }
    }
  }

  fun addPreferences(preferencesList: Iterable<TextMatePreferences>) {
    for (preferences in preferencesList) {
      val scopeName = interner.intern(preferences.scopeName)
      val internedHighlightingPairs = preferences.highlightingPairs?.let { pairs ->
        if (pairs.isEmpty()) {
          emptySet()
        }
        else {
          pairs.mapTo(HashSet(pairs.size)) { it.copy(left = interner.intern(it.left), right = interner.intern(it.right)) }
        }
      }
      val internedSmartTypingPairs = preferences.smartTypingPairs?.let { pairs ->
        if (pairs.isEmpty()) {
          emptySet()
        }
        else {
          pairs.mapTo(HashSet(pairs.size)) { it.copy(left = interner.intern(it.left.toString()), right = interner.intern(it.right.toString())) }
        }
      }
      val internedSurroundingPairs = preferences.surroundingPairs?.let { pairs ->
        if (pairs.isEmpty()) {
          emptySet()
        }
        else {
          pairs.mapTo(HashSet(pairs.size)) { it.copy(left = interner.intern(it.left), right = interner.intern(it.right)) }
        }
      }
      preferencesBuilder.add(Preferences(scopeName,
                                         internedHighlightingPairs,
                                         internedSmartTypingPairs,
                                         internedSurroundingPairs,
                                         preferences.autoCloseBefore,
                                         preferences.indentationRules,
                                         preferences.onEnterRules))
      for (variable in preferences.variables) {
        shellVariablesRegistryBuilder.addVariable(variable)
      }
      val customHighlightingAttributes = preferences.customHighlightingAttributes
      if (customHighlightingAttributes != null) {
        customHighlightingColorsBuilder.put(scopeName, TextMateTextAttributesAdapter(scopeName, customHighlightingAttributes))
      }
    }
  }

  /**
   * The registered bundles that are not loaded and provide a scope that a loaded grammar includes.
   */
  fun unloadedIncludedBundles(): List<RegisteredBundle> {
    return syntaxTableBuilder.unresolvedIncludedScopeNames().mapNotNull { scopeName ->
      bundlesByScopeName.get(scopeName.toString())?.takeIf { !it.loaded }
    }.distinct()
  }

  fun rebuildTables() {
    syntaxTable = syntaxTableBuilder.build()
    preferenceRegistry = preferencesBuilder.build()
    snippetRegistry = snippetsRegistryBuilder.build()
    shellVariablesRegistry = shellVariablesRegistryBuilder.build()
    customHighlightingColors = java.util.Map.copyOf(customHighlightingColorsBuilder)
    fileNameMapping = if (declaredBundles.isEmpty()) {
      baseMapping
    }
    else {
      val mapping = HashMap(baseMapping)
      for (bundle in declaredBundles) {
        for ((extension, scopeName) in bundle.listedExtensionScopes) {
          mapping.put(TextMateFileNameMatcher.Extension(extension), scopeName)
        }
      }
      // a declared extension that no grammar lists gets the first scope of its bundle, unless another bundle maps it
      for (bundle in declaredBundles) {
        val firstScopeName = bundle.firstScopeName ?: continue
        for (extension in bundle.extensions) {
          mapping.putIfAbsent(TextMateFileNameMatcher.Extension(extension), firstScopeName)
        }
      }
      java.util.Map.copyOf(mapping)
    }
  }
}

private sealed class FileNameMatch {
  class Scope(val scopeName: CharSequence) : FileNameMatch()

  /**
   * A declared plugin bundle that is not loaded yet. The scope of the file is known after the load.
   */
  class UnloadedBundle(val bundle: RegisteredBundle.Declared) : FileNameMatch()
}

private sealed class RegisteredBundle(val bundleToLoad: TextMateBundleToLoad) {
  val loadJob = AtomicReference<Job?>(null)

  @Volatile
  var loaded = false

  /**
   * A bundle of the bundles directory. The grammars and the preferences are read when the bundle loads.
   */
  class Builtin(bundleToLoad: TextMateBundleToLoad, val entry: TextMateBundleIndexEntry) : RegisteredBundle(bundleToLoad)

  /**
   * A user or a plugin bundle. The grammar list and the preferences are read at the registration.
   */
  class External(
    bundleToLoad: TextMateBundleToLoad,
    val reader: TextMateBundleReader,
    val grammars: List<TextMateGrammar>,
    val preferences: List<TextMatePreferences>,
  ) : RegisteredBundle(bundleToLoad) {
    val entry: TextMateBundleIndexEntry = bundleIndexEntry(bundleToLoad.name, grammars)
  }

  /**
   * A plugin bundle whose provider declares the file extensions. Nothing of the bundle is read before the bundle loads.
   *
   * @param extensions the declared extensions in lower case
   */
  class Declared(bundleToLoad: TextMateBundleToLoad, val extensions: Set<String>) : RegisteredBundle(bundleToLoad) {
    /**
     * The scope of every declared extension that a grammar of the bundle lists. The load fills it.
     */
    @Volatile
    var listedExtensionScopes: Map<String, CharSequence> = emptyMap()

    /**
     * The scope of the first grammar of the bundle. The load fills it.
     */
    @Volatile
    var firstScopeName: CharSequence? = null
  }
}

private fun listedExtensionScopes(extensions: Set<String>, grammars: List<Pair<TextMateGrammar, CharSequence>>): Map<String, CharSequence> {
  val scopes = HashMap<String, CharSequence>()
  for (extension in extensions) {
    grammars.firstOrNull { (grammar, _) -> grammar.listsExtension(extension) }?.let { scopes.put(extension, it.second) }
  }
  return scopes
}

private fun TextMateGrammar.listsExtension(extension: String): Boolean {
  return fileNameMatchers.any { matcher ->
    when (matcher) {
      is TextMateFileNameMatcher.Extension -> matcher.extension.equals(extension, ignoreCase = true)
      is TextMateFileNameMatcher.Name, is TextMateFileNameMatcher.Pattern -> false
    }
  }
}

private val bundledBundlePath: Path
  get() = PluginPathManager.getPluginHome("textmate")
    .toPath().resolve("lib/bundles").normalize()

private fun readBuiltinBundlesIndex(bundlesPath: Path): TextMateBundlesIndex {
  val indexFile = bundlesPath.resolve(BUNDLES_INDEX_FILE_NAME)
  check(indexFile.isRegularFile()) { "The TextMate bundles index does not exist: $indexFile" }
  return TextMateBundlesIndex.decode(indexFile.readText())
}

private fun builtinBundleToLoad(bundlesPath: Path, entry: TextMateBundleIndexEntry): TextMateBundleToLoad {
  return TextMateBundleToLoad(entry.name, bundlesPath.resolve(entry.name).toString())
}

private fun fireFileTypesChangedEvent(reason: @NonNls String) {
  ApplicationManager.getApplication().invokeLater(
    {
      ApplicationManager.getApplication().runWriteAction {
        val fileTypeManager = FileTypeManager.getInstance() as FileTypeManagerImpl
        fileTypeManager.makeFileTypesChange(reason) {}
      }
    }, ModalityState.nonModal())
}

/**
 * The built-in bundles from the index, see [TextMateBundlesIndex].
 * The list is kept in [TextMateBuiltinBundlesSettings.builtinBundles] for the settings page.
 */
internal fun discoverBuiltinBundles(builtinBundlesSettings: TextMateBuiltinBundlesSettings): List<TextMateBundleToLoad> {
  val builtinBundles = builtinBundlesSettings.builtinBundles
  if (builtinBundles.isNotEmpty()) {
    return builtinBundles
  }

  val bundlesPath = bundledBundlePath
  val bundles = readBuiltinBundlesIndex(bundlesPath).bundles.map { builtinBundleToLoad(bundlesPath, it) }
  builtinBundlesSettings.builtinBundles = bundles
  return bundles
}
