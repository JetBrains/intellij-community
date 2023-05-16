package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.spell.lists.FrequencyMetadata
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import com.intellij.searchEverywhereMl.typos.splitText
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider

@Service(Service.Level.APP)
internal class ActionsLanguageModel(private val actionsDictionary: ActionsDictionary = ActionsDictionaryImpl()) : Dictionary by actionsDictionary,
                                                                                                                  FrequencyMetadata by actionsDictionary {

  companion object {
    /**
     * Returns null if the application is not in an internal mode
     */
    fun getInstance(): ActionsLanguageModel? {
      if (!isTypoFixingEnabled) return null
      return service<ActionsLanguageModel>()
    }
  }

  private val languageModelComputationTask = LanguageModelComputationTask()  // TODO: Change to a coroutine

  init {
    ApplicationManager.getApplication().executeOnPooledThread(languageModelComputationTask)
  }

  /**
   * Returns true if all words from actions and options have been processed and added to the dictionary
   */
  val isComputed: Boolean
    get() = languageModelComputationTask.isFinished

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

  private inner class LanguageModelComputationTask : Runnable {
    var isFinished: Boolean = false

    override fun run() {
      (getWordsFromActions() + getWordsFromSettings())
        .flatMap {
          splitText(it)
            .filter { token -> token is SearchEverywhereStringToken.Word }
            .map { token -> token.value }
        }
        .map { it.filter { c -> c.isLetterOrDigit() } }
        .filterNot { it.isEmpty() || it.isSingleCharacter() }
        .map { it.lowercase() }
        .forEach(actionsDictionary::addWord)

      isFinished = true
    }
  }
}

private class RuntimeActionsDictionaryProvider : RuntimeDictionaryProvider {
  override fun getDictionaries(): Array<Dictionary> {
    return serviceIfCreated<ActionsLanguageModel>()?.let { arrayOf(it) } ?: arrayOf()
  }
}

private fun getProject(): Project = IdeFocusManager.getGlobalInstance().lastFocusedFrame?.project
                                    ?: ProjectManager.getInstance().defaultProject

private fun String.isSingleCharacter(): Boolean = this.length == 1