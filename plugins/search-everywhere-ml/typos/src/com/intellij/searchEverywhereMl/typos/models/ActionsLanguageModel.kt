package com.intellij.searchEverywhereMl.typos.models

import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.searchEverywhereMl.typos.SearchEverywhereStringToken
import com.intellij.searchEverywhereMl.typos.TyposBundle
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import com.intellij.searchEverywhereMl.typos.splitText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import java.util.regex.Pattern

@Service(Service.Level.APP)
internal class ActionsLanguageModel(val coroutineScope: CoroutineScope) {
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

  val deferredDictionary: Deferred<LanguageModelDictionary> = coroutineScope.async {
    val project = guessProject()
    if (project == null) {
      computeLanguageModelDictionary()
    }
    else {
      withBackgroundProgress(project,
                             TyposBundle.getMessage("progress.title.computing.actions.language.model"),
                             cancellable = false) {
        computeLanguageModelDictionary()
      }
    }
  }

  // Accept any word that consist of only alphabetical characters, that are between 3 and 45 characters long
  private val acceptableWordsPattern = Pattern.compile("^[a-zA-Z]{3,45}\$")

  private suspend fun getWordsFromActions(): Sequence<@NlsActions.ActionText String> {
    return (serviceAsync<ActionManager>() as ActionManagerImpl).actionsOrStubs()
      .filterNot { it is ActionGroup && !it.isSearchable }
      .mapNotNull {
        val presentation = it.templatePresentation
        it.applyTextOverride(ActionPlaces.ACTION_SEARCH, presentation)
        return@mapNotNull presentation.text
      }
  }

  private suspend fun getWordsFromSettings(): Sequence<@NlsContexts.ConfigurableName CharSequence> {
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as? SearchableOptionsRegistrarImpl
    if (registrar == null) {
      thisLogger().warn("Failed to cast SearchableOptionsRegistrar")
      return emptySequence()
    }

    registrar.initialize()
    return registrar.getAllOptionNames().asSequence()
  }

  private suspend fun computeLanguageModelDictionary(): LanguageModelDictionary {
    return (getWordsFromActions() + getWordsFromSettings())
      .flatMap {
        splitText(it)
          .filter { token -> token is SearchEverywhereStringToken.Word }
          .map { token -> token.value }
      }
      .filter { acceptableWordsPattern.matcher(it).matches() }
      .map { it.lowercase() }
      .groupingBy { it }.eachCount()
      .let { SimpleLanguageModelDictionary(it) }
  }

  private suspend fun guessProject(): Project? {
    val recentFocusedWindow = (serviceAsync<WindowManager>() as WindowManagerEx).mostRecentFocusedWindow
    if (recentFocusedWindow is IdeFrame) {
      return recentFocusedWindow.project
    }
    else {
      return serviceAsync<ProjectManager>().openProjects.firstOrNull { o -> o.isInitialized && !o.isDisposed }
    }
  }
}
