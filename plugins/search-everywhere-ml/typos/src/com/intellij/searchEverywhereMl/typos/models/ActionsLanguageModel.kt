package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.spell.lists.FrequencyMetadata
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import com.intellij.searchEverywhereMl.typos.splitText
import com.intellij.serviceContainer.NonInjectable
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
internal class ActionsLanguageModel @NonInjectable constructor(private val actionDictionary: ActionDictionary,
                                                               private val project: Project,
                                                               coroutineScope: CoroutineScope) :
  Dictionary by actionDictionary, FrequencyMetadata by actionDictionary {

  @Suppress("unused")
  constructor(project: Project, coroutineScope: CoroutineScope) : this(actionDictionary = ActionDictionaryImpl(), project, coroutineScope)

  companion object {
    /**
     * Returns null if the application is not in an internal mode
     */
    fun getInstance(project: Project): ActionsLanguageModel? {
      if (!isTypoFixingEnabled) {
        return null
      }
      return project.service<ActionsLanguageModel>()
    }
  }

  private val languageModelComputationJob: Job

  init {
    languageModelComputationJob = coroutineScope.launch {
      init()
    }
  }

  /**
   * Returns true if all words from actions and options have been processed and added to the dictionary
   */
  val isComputed: Boolean
    get() = languageModelComputationJob.isCompleted

  private fun getWordsFromActions(): Sequence<@NlsActions.ActionText String> {
    return (ActionManager.getInstance() as ActionManagerImpl).actionsOrStubs()
      .filterNot { it is ActionGroup && !it.isSearchable }
      .mapNotNull { it.templateText }
  }

  private fun getWordsFromSettings(): Sequence<@NlsContexts.ConfigurableName String> {
    return ShowSettingsUtilImpl.configurables(project = project, withIdeSettings = true, checkNonDefaultProject = true)
      .filterIsInstance<SearchableConfigurable>()
      .mapNotNull { it.displayNameFast }
  }

  internal interface ActionDictionary : Dictionary, FrequencyMetadata {
    fun addWord(word: String)
  }

  private class ActionDictionaryImpl : ActionDictionary {
    private val words = Object2IntOpenHashMap<String>(1500)

    override fun addWord(word: String) {
      words.addTo(word, 1)
    }

    override fun getName(): String = "Actions Dictionary"

    override fun contains(word: String): Boolean = words.containsKey(word)

    override fun getWords(): Set<String> = words.keys.toSet()

    override val defaultFrequency: Int = 0

    override val maxFrequency: Int
      get() = words.maxOf { it.value }

    override fun getFrequency(word: String): Int? = if (words.containsKey(word)) words.getInt(word) else null
  }

  private fun init() {
    (getWordsFromActions() + getWordsFromSettings())
      .flatMap {
        splitText(it)
          .filter { token -> token is SearchEverywhereStringToken.Word }
          .map { token -> token.value }
      }
      .map { it.filter { c -> c.isLetterOrDigit() } }
      .filterNot { it.isEmpty() || it.isSingleCharacter() }
      .map { it.lowercase() }
      .forEach(actionDictionary::addWord)
  }
}

private class ModelComputationStarter : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    delay(30.seconds)
    if (isTypoFixingEnabled) {
      project.serviceAsync<ActionsLanguageModel>()
    }
  }
}


private class RuntimeActionsDictionaryProvider : RuntimeDictionaryProvider {
  override fun getDictionaries(): Array<Dictionary> {
    return serviceIfCreated<ActionsLanguageModel>()?.let { arrayOf(it) } ?: arrayOf()
  }
}

private fun String.isSingleCharacter(): Boolean = this.length == 1