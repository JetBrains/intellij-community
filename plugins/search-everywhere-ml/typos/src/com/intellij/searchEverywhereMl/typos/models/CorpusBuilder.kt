package com.intellij.searchEverywhereMl.typos.models

import com.intellij.ide.actions.ShowSettingsUtilImpl.Companion.getConfigurables
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.ide.util.gotoByName.getAnActionText
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ex.ConfigurableWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.searchEverywhereMl.typos.TyposBundle
import com.intellij.searchEverywhereMl.typos.isTypoFixingEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

@Service(Service.Level.APP)
internal class CorpusBuilder(private val coroutineScope: CoroutineScope) {

  companion object {
    /**
     * Returns null if typo-tolerant search is disabled in the Advanced Settings
     */
    fun getInstance(): CorpusBuilder? {
      if (!isTypoFixingEnabled) {
        return null
      }
      return service<CorpusBuilder>()
    }
  }

  suspend fun buildCorpus(): Set<List<String>> {
    val project = guessProject()
    return if (project == null) {
      computeCorpus()
    }
    else {
      withBackgroundProgress(
        project,
        TyposBundle.getMessage("progress.title.computing.actions.language.model"),
        TaskCancellation.nonCancellable(),
        suspender = null,
        visibleInStatusBar = false
      ) {
        computeCorpus()
      }
    }
  }

  /**
   * Computes the corpus by aggregating and tokenizing data from actions and settings
   */
  private suspend fun computeCorpus(): Set<List<String>> {
    val actionWordsDeferred = coroutineScope.async { getWordsFromActions() }
    val settingWordsDeferred = coroutineScope.async { getWordsFromSettings() }

    val actionWords = actionWordsDeferred.await()
    val settingWords = settingWordsDeferred.await()

    return actionWords + settingWords
  }


  private suspend fun getWordsFromActions(): Set<List<String>> {
    val actionManager = serviceAsync<ActionManager>() as? ActionManagerImpl
    if (actionManager == null) {
      return emptySet()
    }
    return actionManager.actionsOrStubs()
      .filterNot { it is ActionGroup && !it.isSearchable }
      .mapNotNull { action ->
        val text = getAnActionText(action)
        text?.let { tokenizeText(it) }
      }
      .toSet()
  }

  private suspend fun getWordsFromSettings(): Set<List<String>> {
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as? SearchableOptionsRegistrarImpl
    if (registrar == null) {
      return emptySet()
    }

    registrar.initialize()
    val storage = registrar.getStorage()
    val uniqueSentences = mutableSetOf<List<String>>()

    // Process options registry descriptions
    storage.forEach { (_, descriptions) ->
      descriptions.forEach { optionDescription ->
        val hit = optionDescription.hit
        if (hit != null) {
          tokenizeText(hit)?.let { uniqueSentences.add(it) }
        }
      }
    }

    // Process configurables
    val allConfigurables = getConfigurables(guessProject(), true, true)
    allConfigurables.forEach { configurable ->
      uniqueSentences.addAll(getConfigurableDetails(configurable))
    }

    return uniqueSentences

  }

  private fun getConfigurableDetails(configurable: Configurable): Set<List<String>> {
    val collectedTokens = mutableSetOf<List<String>>()

    configurable.displayNameFast?.let { name ->
      tokenizeText(name)?.let { collectedTokens.add(it) }
    }

    if (configurable is ConfigurableWrapper) {
      configurable.extensionPoint.pluginDescriptor?.description
        ?.replace(HTML_TAGS_REGEX, "") // matches everything enclosed in < and > and removes any HTML-like tags.
        ?.split('.', '\n')
        ?.mapNotNull { tokenizeText(it) }
        ?.let { collectedTokens.addAll(it) }
    }

    if (configurable is Configurable.Composite) {
      configurable.getConfigurables().forEach { child ->
        collectedTokens.addAll(getConfigurableDetails(child))
      }
    }

    return collectedTokens
  }
  private val HTML_TAGS_REGEX = Regex("<[^>]*>")

  private fun tokenizeText(text: String): List<String>? =
    tokenizeTextForTypoLookup(text)
      .takeIf { it.isNotEmpty() }

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
