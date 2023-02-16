package com.intellij.ide.actions.searcheverywhere.ml.typos

import ai.grazie.spell.lists.FrequencyMetadata
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.progress.withBackgroundProgress
import com.intellij.openapi.progress.withRawProgressReporter
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
import kotlinx.coroutines.*
import kotlin.coroutines.EmptyCoroutineContext

@Service(Service.Level.APP)
internal class ActionsLanguageModel(private val actionsDictionary: ActionsDictionary = ActionsDictionaryImpl()) : Disposable,
                                                                                                                  Dictionary by actionsDictionary,
                                                                                                                  FrequencyMetadata by actionsDictionary {

  companion object {
    /**
     * Returns null if the application is not in an internal mode
     */
    fun getInstance(): ActionsLanguageModel? {
      if (!ApplicationManager.getApplication().isInternal) return null
      return service<ActionsLanguageModel>()
    }
  }

  private val coroutineScope = CoroutineScope(EmptyCoroutineContext)

  private val languageModelComputation = coroutineScope.async {
    val project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project ?: ProjectManager.getInstance().defaultProject

    withBackgroundProgress(project, TyposBundle.getMessage("progress.title.computing.language.model"), true) {
      withRawProgressReporter {
        (getWordsFromActions() + getWordsFromSettings())
          .flatMap { it.split(" ", "-", ".") }
          .map { it.filter { c -> c.isLetterOrDigit() } }
          .filterNot { it.isEmpty() || it.isSingleCharacter() }
          .map { it.lowercase() }
          .forEach(actionsDictionary::addWord)
      }
    }
  }

  /**
   * Returns true if all words from actions and options have been processed and added to the dictionary
   */
  val isComputed
    get() = languageModelComputation.isCompleted

  private fun getWordsFromActions() = (ActionManager.getInstance() as ActionManagerImpl)
    .let { actionManager ->
      actionManager.actionIds
        .asSequence()
        .mapNotNull { actionManager.getAction(it) }
    }.filterNot { it is ActionGroup && !it.isSearchable }
    .mapNotNull { it.templateText }


  private fun getWordsFromSettings() = ShowSettingsUtilImpl.getConfigurables(null, true, false)
    .asSequence()
    .filterIsInstance<SearchableConfigurable>()
    .map { it.displayName }

  override fun dispose() {
    coroutineScope.cancel()
  }

  internal interface ActionsDictionary : Dictionary, FrequencyMetadata {
    fun addWord(word: String)
  }

  private class ActionsDictionaryImpl : ActionsDictionary {
    private val words: MutableMap<String, Int> = HashMap(1500)

    override fun addWord(word: String) {
      words[word] = (words[word] ?: 0) + 1
    }

    override fun getName(): String = "Actions Dictionary"

    override fun contains(word: String): Boolean = words.containsKey(word)

    override fun getWords(): Set<String> = words.keys.toSet()

    override val defaultFrequency: Int = 0

    override val maxFrequency: Int
      get() = words.maxOf { it.value }

    override fun getFrequency(word: String): Int? = words[word]
  }

  @Suppress("unused")  // Registered in the plugin's XML file
  private class ModelComputationStarter : ProjectActivity {
    override suspend fun execute(project: Project) {
      getInstance()
    }
  }
}

private class RuntimeActionsDictionaryProvider : RuntimeDictionaryProvider {
  override fun getDictionaries(): Array<Dictionary> {
    return serviceIfCreated<ActionsLanguageModel>()?.let { arrayOf(it) } ?: arrayOf()
  }
}

private fun String.isSingleCharacter(): Boolean = this.length == 1