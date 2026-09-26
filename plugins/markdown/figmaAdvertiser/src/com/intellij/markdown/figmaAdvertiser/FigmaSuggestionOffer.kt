package com.intellij.markdown.figmaAdvertiser

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserUsagesCollector.SuggestionSurface
import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserUsagesCollector.SuggestionTrigger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorNotifications
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * What the offer says, and what each action does when the user takes it.
 *
 * The provider turns these answers into Swing. Everything that decides something lives outside the
 * panel so that it can be asked without one.
 */
@ApiStatus.Internal
class FigmaSuggestionOffer(
  private val project: Project,
  private val trigger: SuggestionTrigger,
  private val surface: SuggestionSurface,
) {
  private val pluginId = PluginId.getId(FIGMA_CONNECT_PLUGIN_ID)

  /**
   * The plugin's descriptor when it is on disk, switched on or off.
   *
   * On disk is what the offer's own word has to follow, and `isDisabled` answers a different
   * question. The banner is drawn whenever the plugin is not **loaded**
   * (`buildSuggestionIfNeeded` filters on `PluginManager.getLoadedPlugins()`), so a plugin on disk
   * and switched on still reaches it, and offering that user an install would send them to
   * Marketplace for a plugin they already have.
   */
  private val installedPlugin: IdeaPluginDescriptor? = PluginManagerCore.getPlugin(pluginId)

  val text: @Nls String = figmaSuggestionText()

  val primaryActionText: @Nls String =
    if (installedPlugin == null) {
      IdeBundle.message("plugins.advertiser.action.install.plugin.name", FIGMA_CONNECT_PLUGIN_NAME)
    }
    else {
      MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.action.enable", FIGMA_CONNECT_PLUGIN_NAME)
    }

  val dismissActionText: @Nls String = IdeBundle.message("plugins.advertiser.action.ignore.ultimate")

  /** The offer is on screen. */
  fun reportShown() {
    FigmaAdvertiserUsagesCollector.logSuggestionShown(project, trigger, surface)
  }

  /**
   * Installs the plugin, or turns an installed one back on, and opens the setup wizard.
   *
   * Runs on the EDT: a `createActionLabel` click is what calls this.
   *
   * **The restart offer is the platform's own, and nothing is added here.** `installAndEnable` runs
   * the success body through `InstallAndEnableTask.runOnSuccess`, which is given the result of
   * `PluginInstaller.installAndLoadDynamicPlugin` — false when the plugin needs a restart. On that
   * branch `PluginsAdvertiserDialogPluginInstaller` has already run
   * `PluginManagerMain.notifyPluginsUpdated`, whose notification carries the platform's own
   * "Restart to activate plugin updates" action, so the restart is asked for exactly once.
   *
   * `PluginManagerMain` is `@ApiStatus.Internal` and a bundled plugin already calls it:
   * `plugins/hunspell/src/com/intellij/hunspell/HunspellStartupActivity.kt:55`.
   *
   * `DynamicPluginEnabler.enable`, which is what `PluginEnabler.getInstance()` answers in a running
   * IDE, reports whether the descriptors were **loaded**, and it reports it correctly only when it is
   * called on the EDT: its `runInEdt { … }` runs the block inline there, and the value it reads back
   * is the block's. `getInstance()` answers `DisabledPluginsState` before
   * `LoadingState.COMPONENTS_LOADED` and on a disposed application, and that one reports whether the
   * disabled set changed instead; a banner click reaches neither state. This path runs no installer,
   * so it raises the platform's notification itself.
   */
  fun accept() {
    val source = surface.eventSource()
    FigmaAdvertiserUsagesCollector.logSuggestionAccepted(project, trigger, surface)
    val installed = installedPlugin
    if (installed == null) {
      source.logInstallPlugins(listOf(FIGMA_CONNECT_PLUGIN_ID), project)
      installAndEnable(project, setOf(pluginId), showDialog = true) { openSetupWizard() }
      return
    }
    // Nothing is downloaded on this branch, so the platform's record of the click is an enable.
    source.logEnablePlugins(listOf(FIGMA_CONNECT_PLUGIN_ID), project)
    // One call for both on-disk states. `enable` flips the disabled flag when there is one to flip
    // and then loads the descriptor, so a plugin that is on disk and switched on is loaded in this
    // session rather than sent to a restart it does not need.
    if (PluginEnabler.getInstance().enable(listOf(installed))) {
      openSetupWizard()
    }
    else {
      PluginManagerMain.notifyPluginsUpdated(project)
    }
  }

  /** Records the answer on the project, and takes the banner down. */
  fun dismiss() {
    surface.eventSource().logIgnoreExtension(project)
    FigmaAdvertiserUsagesCollector.logSuggestionDismissed(project, trigger, surface)
    dismissFigmaSuggestion(project)
    EditorNotifications.getInstance(project).updateAllNotifications()
  }

  /**
   * Opens the Figma tool window, which is where the setup wizard is shown.
   *
   * `FigmaWelcomeSurface` picks the wizard over the recap panel when `FigmaToolWindowContent` is
   * built, so the window is the wizard's entry point for a user who has not completed it on this
   * machine. A user who has completed it before, or one whose IDE has `figma.setup.wizard.enabled`
   * off, gets the recap panel instead, which is the first-run surface for them.
   *
   * **The `?.` is load-bearing.** `PluginManagerMain.downloadPluginsImpl` schedules the runnable
   * that ANDs in the dynamic-load result and the one that calls back here under different modality
   * states, so which runs first is not guaranteed. A plugin that did not load registers no tool
   * window, `getToolWindow` answers null, and this call does nothing.
   */
  private fun openSetupWizard() {
    ToolWindowManager.getInstance(project).getToolWindow(FIGMA_TOOL_WINDOW_ID)?.activate(null)
    EditorNotifications.getInstance(project).updateAllNotifications()
  }
}

@ApiStatus.Internal
fun figmaSuggestionText(): @Nls String = MarkdownFigmaAdvertiserBundle.message("markdown.figma.suggestion.text")

/**
 * Owned by Figma Connect (`plugins/figma/frontend/resources/intellij.figma.frontend.xml:65`) and
 * repeated here, because this module must not depend on the plugin it advertises: the offer exists
 * for the case where that plugin is absent, and a content module whose dependency is missing is
 * dropped silently. It is what `ActivateToolWindowAction` is keyed on, so it does not move quietly.
 */
private const val FIGMA_TOOL_WINDOW_ID: String = "Figma"
