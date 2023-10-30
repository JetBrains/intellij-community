package com.intellij.searchEverywhereMl.typos.models

import ai.grazie.spell.lists.FrequencyMetadata
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken
import com.intellij.searchEverywhereMl.typos.TyposBundle
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import com.intellij.searchEverywhereMl.typos.splitText
import com.intellij.serviceContainer.NonInjectable
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.RuntimeDictionaryProvider
import com.intellij.util.alsoIfNull
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.regex.Pattern

@Service(Service.Level.APP)
internal class ActionsLanguageModel @NonInjectable constructor(private val actionDictionary: ActionDictionary,
                                                               coroutineScope: CoroutineScope) :
  Dictionary by actionDictionary, FrequencyMetadata by actionDictionary {

  @Suppress("unused")
  constructor(coroutineScope: CoroutineScope) : this(actionDictionary = ActionDictionaryImpl(), coroutineScope)

  companion object {
    /**
     * Returns null if the application is not in an internal mode
     */
    fun getInstance(): ActionsLanguageModel? {
      if (!isTypoFixingEnabled) {
        return null
      }
      return service<ActionsLanguageModel>()
    }
  }

  // Accept any word that consist of only alphabetical characters, that are between 3 and 45 characters long
  private val acceptableWordsPattern = Pattern.compile("^[a-zA-Z]{3,45}\$")

  private val languageModelComputationJob: Job

  init {
    languageModelComputationJob = coroutineScope.launch {
      guessProject()?.also {
        withBackgroundProgress(project = it,
                               title = TyposBundle.getMessage("progress.title.computing.actions.language.model"),
                               cancellable = false) { init() }
      }.alsoIfNull {
        init()
      }
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

  private fun getWordsFromSettings(): Sequence<@NlsContexts.ConfigurableName CharSequence> {
    val registrar = SearchableOptionsRegistrar.getInstance() as? SearchableOptionsRegistrarImpl
    if (registrar == null) {
      thisLogger().warn("Failed to cast SearchableOptionsRegistrar")
      return emptySequence()
    }

    registrar.initialize()
    return registrar.allOptionNames.asSequence()
  }

  internal interface ActionDictionary : Dictionary, FrequencyMetadata {
    fun addWord(word: String)
  }

  private class ActionDictionaryImpl : ActionDictionary {
    private val words = Object2IntOpenHashMap<String>(12_000)

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

  private fun guessProject(): Project? {
    val recentFocusedWindow = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
    if (recentFocusedWindow is IdeFrame) {
      return (recentFocusedWindow as IdeFrame).project
    }
    else {
      return ProjectManager.getInstance().openProjects.firstOrNull { o -> o.isInitialized && !o.isDisposed }
    }
  }

  private suspend fun init() {
    (getWordsFromActions() + getWordsFromSettings())
      .flatMap {
        splitText(it)
          .filter { token -> token is SearchEverywhereStringToken.Word }
          .map { token -> token.value }
      }
      .filter { acceptableWordsPattern.matcher(it).matches() }
      .map { it.lowercase() }
      .forEach(actionDictionary::addWord)
  }
}


private class RuntimeActionsDictionaryProvider : RuntimeDictionaryProvider {
  override fun getDictionaries(): Array<Dictionary> {
    return serviceIfCreated<ActionsLanguageModel>()?.let { arrayOf(it) } ?: arrayOf()
  }
}
