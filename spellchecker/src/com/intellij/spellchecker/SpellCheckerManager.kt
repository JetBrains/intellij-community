// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.spellchecker

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.project.getProjectStoreDirectory
import com.intellij.spellchecker.SpellCheckerManager.Companion.restartInspections
import com.intellij.spellchecker.dictionary.*
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.engine.SpellCheckerEngine
import com.intellij.spellchecker.engine.SuggestionProvider
import com.intellij.spellchecker.grazie.GrazieSpellCheckerEngine
import com.intellij.spellchecker.grazie.GrazieSuggestionProvider
import com.intellij.spellchecker.settings.SpellCheckerSettings
import com.intellij.spellchecker.state.AppDictionaryState
import com.intellij.spellchecker.state.DictionaryStateListener
import com.intellij.spellchecker.state.ProjectDictionaryState
import com.intellij.spellchecker.util.SpellCheckerBundle
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.Nls
import java.io.File
import java.util.*
import java.util.function.Consumer
import java.util.function.Supplier

private val LOG = logger<SpellCheckerManager>()
private val BUNDLED_EP_NAME = ExtensionPointName<BundledDictionaryProvider>("com.intellij.spellchecker.bundledDictionaryProvider")

@Service(Service.Level.PROJECT)
class SpellCheckerManager(val project: Project) : Disposable {
  private var projectDictionary: ProjectDictionary? = null
  private var appDictionary: EditableDictionary? = null

  internal val projectDictionaryPath: String
  internal val appDictionaryPath: String

  private val userDictionaryListenerEventDispatcher = EventDispatcher.create(DictionaryStateListener::class.java)

  // used in Rider
  @get:Suppress("unused")
  var spellChecker: SpellCheckerEngine? = null
    private set

  private var suggestionProvider: SuggestionProvider? = null

  init {
    fullConfigurationReload()
    @Suppress("DEPRECATION")
    val projectStoreDir = project.baseDir?.let { getProjectStoreDirectory(it) }
    projectDictionaryPath = if (projectStoreDir == null) "" else projectStoreDir.path + File.separator + PROJECT_DICTIONARY_PATH
    appDictionaryPath = PathManager.getOptionsPath() + File.separator + CACHED_DICTIONARY_FILE
    LocalFileSystem.getInstance().addVirtualFileListener(CustomDictFileListener(project = project, manager = this), this)
    BUNDLED_EP_NAME.addChangeListener({ fillEngineDictionary(spellChecker!!) }, this)
    RuntimeDictionaryProvider.EP_NAME.addChangeListener({ fillEngineDictionary(spellChecker!!) }, this)
    CustomDictionaryProvider.EP_NAME.addChangeListener({ fillEngineDictionary(spellChecker!!) }, this)
  }

  companion object {
    private const val MAX_METRICS = 1
    private val PROJECT_DICTIONARY_PATH = "dictionaries${File.separator}${System.getProperty("user.name").replace('.', '_')}.xml"
    private const val CACHED_DICTIONARY_FILE = "spellchecker-dictionary.xml"

    @JvmStatic
    fun getInstance(project: Project): SpellCheckerManager = project.service()

    @JvmStatic
    val bundledDictionaries: List<String>
      get() = BUNDLED_EP_NAME.extensionList.flatMap { it.bundledDictionaries.asSequence() }

    @JvmStatic
    val runtimeDictionaries: List<Dictionary>
      get() = RuntimeDictionaryProvider.EP_NAME.extensionList.flatMap { it.dictionaries.asSequence() }

    fun restartInspections() {
      ApplicationManager.getApplication().invokeLater {
        val projects = ProjectManager.getInstance().openProjects
        for (project in projects) {
          if (project.isInitialized && project.isOpen) {
            DaemonCodeAnalyzer.getInstance(project).restart()
          }
        }
      }
    }

    private fun findApplicable(path: String): CustomDictionaryProvider? {
      return CustomDictionaryProvider.EP_NAME.extensionList.firstOrNull { it.isApplicable(path) }
    }
  }

  fun fullConfigurationReload() {
    val spellChecker = project.service<GrazieSpellCheckerEngine>()
    this.spellChecker = spellChecker
    suggestionProvider = GrazieSuggestionProvider(spellChecker)
    fillEngineDictionary(spellChecker)
  }

  fun updateBundledDictionaries(removedDictionaries: List<String?>) {
    val spellChecker = spellChecker!!
    for (provider in BUNDLED_EP_NAME.extensionList) {
      for (dictionary in provider.bundledDictionaries) {
        if (!spellChecker.isDictionaryLoad(dictionary)) {
          loadBundledDictionary(provider = provider, dictionary = dictionary, spellChecker = spellChecker)
        }
      }
    }

    val settings = SpellCheckerSettings.getInstance(project)
    for (provider in RuntimeDictionaryProvider.EP_NAME.extensionList) {
      for (dictionary in provider.dictionaries) {
        val dictionaryShouldBeLoad = !settings.runtimeDisabledDictionariesNames.contains(dictionary.name)
        val dictionaryIsLoad = spellChecker.isDictionaryLoad(dictionary.name)
        if (dictionaryIsLoad && !dictionaryShouldBeLoad) {
          spellChecker.removeDictionary(dictionary.name)
        }
        else if (!dictionaryIsLoad && dictionaryShouldBeLoad) {
          spellChecker.addDictionary(dictionary)
        }
      }
    }

    if (settings.customDictionariesPaths != null) {
      for (dictionary in settings.customDictionariesPaths) {
        if (!spellChecker.isDictionaryLoad(dictionary)) {
          loadDictionary(dictionary)
        }
      }
    }

    if (!removedDictionaries.isEmpty()) {
      for (name in removedDictionaries) {
        spellChecker.removeDictionary(name!!)
      }
    }

    restartInspections()
  }

  val userDictionaryWords: Set<String>
    get() = projectDictionary!!.editableWords + appDictionary!!.editableWords

  private fun fillEngineDictionary(spellChecker: SpellCheckerEngine) {
    spellChecker.reset()
    val settings = SpellCheckerSettings.getInstance(project)
    loadBundledDictionaries(spellChecker)
    for (provider in RuntimeDictionaryProvider.EP_NAME.extensionList) {
      for (dictionary in provider.dictionaries) {
        if (!settings.runtimeDisabledDictionariesNames.contains(dictionary.name)) {
          spellChecker.addDictionary(dictionary)
        }
      }
    }
    if (settings.customDictionariesPaths != null) {
      for (path in settings.customDictionariesPaths) {
        loadDictionary(path)
      }
    }

    // Load custom dictionaries
    initUserDictionaries(spellChecker)
  }

  private fun initUserDictionaries(spellChecker: SpellCheckerEngine) {
    val appDictionaryState = AppDictionaryState.getInstance()
    appDictionaryState.addAppDictListener({ restartInspections() }, this)
    if (appDictionaryState.dictionary == null) {
      appDictionaryState.dictionary = UserDictionary(AppDictionaryState.DEFAULT_NAME)
    }
    appDictionary = appDictionaryState.dictionary
    spellChecker.addModifiableDictionary(appDictionary!!)
    val dictionaryState = project.service<ProjectDictionaryState>()
    dictionaryState.addProjectDictListener { restartInspections() }
    projectDictionary = dictionaryState.projectDictionary
    projectDictionary!!.setActiveName(System.getProperty("user.name"))
    spellChecker.addModifiableDictionary(projectDictionary!!)
  }

  internal fun loadDictionary(path: String) {
    val dictionaryProvider = findApplicable(path)
    if (dictionaryProvider == null) {
      spellChecker!!.loadDictionary(FileLoader(path))
    }
    else {
      val dictionary = dictionaryProvider.get(path)
      if (dictionary != null) {
        spellChecker!!.addDictionary(dictionary)
      }
    }
  }

  fun hasProblem(word: String): Boolean = !spellChecker!!.isCorrect(word)

  fun acceptWordAsCorrect(word: String, project: Project) {
    acceptWordAsCorrect(word = word, file = null, project = project, dictionaryLevel = DictionaryLevel.PROJECT) // TODO: or default
  }

  internal fun acceptWordAsCorrect(word: String, file: VirtualFile?, project: Project, dictionaryLevel: DictionaryLevel) {
    if (DictionaryLevel.NOT_SPECIFIED == dictionaryLevel) {
      return
    }

    val transformed = spellChecker!!.transformation.transform(word) ?: return
    val dictionary = if (DictionaryLevel.PROJECT == dictionaryLevel) projectDictionary else appDictionary
    if (file != null) {
      WriteCommandAction.writeCommandAction(project)
        .run<RuntimeException> {
          UndoManager.getInstance(project).undoableActionPerformed(object : BasicUndoableAction(file) {
            override fun undo() {
              removeWordFromDictionary(dictionary!!, transformed)
            }

            override fun redo() {
              addWordToDictionary(dictionary!!, transformed)
            }
          })
        }
    }
    addWordToDictionary(dictionary = dictionary!!, word = transformed)
  }

  private fun addWordToDictionary(dictionary: EditableDictionary, word: String) {
    dictionary.addToDictionary(word)
    fireDictionaryChanged(dictionary)
  }

  private fun removeWordFromDictionary(dictionary: EditableDictionary, transformed: String) {
    dictionary.removeFromDictionary(transformed)
    fireDictionaryChanged(dictionary)
  }

  private fun fireDictionaryChanged(dictionary: EditableDictionary) {
    userDictionaryListenerEventDispatcher.multicaster.dictChanged(dictionary)
    restartInspections()
    SaveAndSyncHandler.getInstance().scheduleProjectSave(project)
  }

  fun updateUserDictionary(words: Collection<String>) {
    // new for project dictionary
    val addedToProjectWords = words - userDictionaryWords
    val projectDictionary = projectDictionary!!
    for (word in addedToProjectWords) {
      projectDictionary.addToDictionary(word)
    }

    val wordSet = words.toHashSet()

    // deleted from project dictionary
    val deletedFromProjectWords = projectDictionary.editableWords - wordSet
    for (word in deletedFromProjectWords) {
      projectDictionary.removeFromDictionary(word)
    }
    if (addedToProjectWords.size + deletedFromProjectWords.size > 0) {
      userDictionaryListenerEventDispatcher.multicaster.dictChanged(projectDictionary)
    }

    // deleted from application dictionary
    val deletedFromApplicationWords = appDictionary!!.editableWords - wordSet
    for (word in deletedFromApplicationWords) {
      appDictionary!!.removeFromDictionary(word)
    }
    if (!deletedFromApplicationWords.isEmpty()) {
      userDictionaryListenerEventDispatcher.multicaster.dictChanged(appDictionary)
    }
    restartInspections()
  }

  fun getSuggestions(text: String): List<String> {
    val correctionLimit = Registry.intValue("spellchecker.corrections.limit", 5)
    return suggestionProvider!!.getSuggestions(text, correctionLimit, MAX_METRICS)
  }

  override fun dispose() {
  }

  fun openDictionaryInEditor(dictPath: String) {
    val file = if (dictPath.isEmpty()) null
    else LocalFileSystem.getInstance().refreshAndFindFileByPath(dictPath)
    if (file == null) {
      val title = SpellCheckerBundle.message("dictionary.not.found.title")
      val message = SpellCheckerBundle.message("dictionary.not.found", dictPath)
      Messages.showMessageDialog(project, message, title, Messages.getWarningIcon())
      return
    }
    FileEditorManager.getInstance(project)?.openFile(file, true)
  }

  @Suppress("unused") // used in Rider
  fun addUserDictionaryChangedListener(listener: DictionaryStateListener, parentDisposable: Disposable?) {
    userDictionaryListenerEventDispatcher.addListener(listener)
    Disposer.register(parentDisposable!!) { userDictionaryListenerEventDispatcher.removeListener(listener) }
  }
}

internal enum class DictionaryLevel(private val nameSupplier: Supplier<@Nls String>) {
  APP(SpellCheckerBundle.messagePointer("dictionary.name.application.level")),
  PROJECT(SpellCheckerBundle.messagePointer("dictionary.name.project.level")),
  NOT_SPECIFIED(SpellCheckerBundle.messagePointer("dictionary.name.not.specified"));

  @Nls
  fun getName(): String = nameSupplier.get()

  companion object {
    private val DICTIONARY_LEVELS = EnumSet.allOf(DictionaryLevel::class.java).associateBy { it.getName() }

    @JvmStatic
    fun getLevelByName(name: String): DictionaryLevel = DICTIONARY_LEVELS.get(name) ?: NOT_SPECIFIED
  }
}

private class CustomDictFileListener(private val project: Project, private val manager: SpellCheckerManager) : VirtualFileListener {
  override fun fileDeleted(event: VirtualFileEvent) {
    removeCustomDictionaries(event.file.path)
  }

  override fun fileCreated(event: VirtualFileEvent) {
    loadCustomDictionaries(event.file)
  }

  override fun fileMoved(event: VirtualFileMoveEvent) {
    val oldPath = event.oldParent.path + File.separator + event.fileName
    if (!affectCustomDictionaries(oldPath, project)) {
      loadCustomDictionaries(event.file)
    }
    else {
      val newPath = event.newParent.path + File.separator + event.fileName
      if (!affectCustomDictionaries(newPath, project)) {
        removeCustomDictionaries(oldPath)
      }
    }
  }

  override fun contentsChanged(event: VirtualFileEvent) {
    val path = FileUtilRt.toSystemDependentName(event.file.path)
    if (!manager.spellChecker!!.isDictionaryLoad(path)) {
      return
    }

    manager.spellChecker!!.removeDictionary(path)
    manager.loadDictionary(path)
    restartInspections()
  }

  override fun propertyChanged(event: VirtualFilePropertyEvent) {
    val file = event.file
    if (file.isDirectory) return
    if (VirtualFile.PROP_NAME == event.propertyName) {
      val oldName = event.oldValue as String
      if (!isDic(oldName)) {
        loadCustomDictionaries(file)
      }
      else {
        val newName = event.newValue as String
        if (!isDic(newName)) {
          removeCustomDictionaries(file.parent.path + File.separator + oldName)
        }
      }
    }
  }

  private fun removeCustomDictionaries(path: String) {
    val systemDependentPath = FileUtilRt.toSystemDependentName(path)
    if (affectCustomDictionaries(path, project)) {
      manager.spellChecker!!.removeDictionariesRecursively(systemDependentPath)
      SpellCheckerSettings.getInstance(project).customDictionariesPaths.removeIf { dict: String? ->
        FileUtil.isAncestor(systemDependentPath, dict!!, false)
      }
      restartInspections()
    }
  }

  private fun loadCustomDictionaries(file: VirtualFile) {
    val path = FileUtilRt.toSystemDependentName(file.path)
    if (!affectCustomDictionaries(path, project)) {
      return
    }

    VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Void?>() {
      override fun visitFile(file: VirtualFile): Boolean {
        val isDirectory = file.isDirectory
        val filePath = file.path
        if (!isDirectory && SpellCheckerSettings.getInstance(project).customDictionariesPaths.contains(filePath)) {
          manager.loadDictionary(filePath)
          restartInspections()
        }
        return isDirectory
      }
    })
  }
}

private fun isDic(path: String): Boolean {
  return FileUtilRt.extensionEquals(path, "dic")
}

private fun affectCustomDictionaries(path: String, project: Project): Boolean {
  return SpellCheckerSettings.getInstance(project).customDictionariesPaths.any { FileUtil.isAncestor(path, it!!, false) }
}

private fun loadBundledDictionaries(spellChecker: SpellCheckerEngine) {
  for (provider in BUNDLED_EP_NAME.extensionList) {
    for (dictionary in provider.bundledDictionaries) {
      loadBundledDictionary(provider = provider, dictionary = dictionary, spellChecker = spellChecker)
    }
  }
}

private fun loadBundledDictionary(provider: BundledDictionaryProvider, dictionary: String, spellChecker: SpellCheckerEngine) {
  spellChecker.loadDictionary(StreamLoader(dictionary, provider.javaClass))
}

private class StreamLoader(private val name: String, private val loaderClass: Class<*>) : Loader {
  override fun load(consumer: Consumer<String>) {
    val stream = loaderClass.getResourceAsStream(name)
    if (stream == null) {
      LOG.warn("Couldn't load dictionary $name with loader '$loaderClass'")
      return
    }

    try {
      stream.reader().useLines { it.forEach(consumer::accept) }
    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  override fun getName() = name
}