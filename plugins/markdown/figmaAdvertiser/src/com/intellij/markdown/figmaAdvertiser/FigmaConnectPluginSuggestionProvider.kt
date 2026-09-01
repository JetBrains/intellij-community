package com.intellij.markdown.figmaAdvertiser

import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserUsagesCollector.SuggestionSurface
import com.intellij.markdown.figmaAdvertiser.FigmaAdvertiserUsagesCollector.SuggestionTrigger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestion
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginSuggestionProvider
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.buildSuggestionIfNeeded
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.annotations.ApiStatus

/**
 * Offers Figma Connect over a Markdown file whose text links to a Figma design.
 *
 * Lives in the Markdown plugin rather than in Figma Connect: a suggestion shipped by the plugin it
 * suggests would only ever run too late.
 *
 * Everything decidable is decided in `FigmaSuggestionDecision.kt` and `FigmaLinkScan.kt`, which are
 * a pure function per question and are where the tests are. The offer this class prints and the
 * actions it carries are [FigmaSuggestionOffer]. This class turns those answers into Swing.
 */
@ApiStatus.Internal
class FigmaConnectPluginSuggestionProvider : PluginSuggestionProvider {

  override fun getSuggestion(project: Project, file: VirtualFile): PluginSuggestion? {
    // The switch first: this is asked about every file that is opened, and a registry read is
    // cheaper than the path match below, which is string work.
    if (!FigmaAdvertiserRegistry.isAdvertiserEnabled) return null
    if (!isMarkdownSuggestionFile(file.path)) return null
    if (isFigmaSuggestionDismissed(project)) return null

    // Asked before the file is read, and it can be: the offer says the same thing over every file,
    // so nothing here needs the text. This drops every plugin id that is already loaded and returns
    // null once the list is empty, which is how a user who already runs Figma Connect is excluded.
    val needed = buildSuggestionIfNeeded(
      project,
      pluginIds = listOf(FIGMA_CONNECT_PLUGIN_ID),
      pluginName = FIGMA_CONNECT_PLUGIN_NAME,
      suggestionText = figmaSuggestionText(),
      suggestionDismissKey = PLATFORM_GUARD_DISMISS_KEY,
    ) ?: return null

    // The file's own text is read last, so nothing above is paid for by a read.
    if (!linksToFigma(file)) return null

    return FigmaConnectPluginSuggestion(
      pluginIds = needed.pluginIds,
      offer = FigmaSuggestionOffer(project, SuggestionTrigger.MARKDOWN_FIGMA_LINK, SuggestionSurface.EDITOR),
    )
  }
}

/**
 * Offers to enable an installed-but-disabled plugin, or to install a missing one, and then opens the
 * tool window so that the click leads somewhere.
 *
 * The platform's own `DefaultPluginSuggestion` is `internal`, and only installs; it also leaves the
 * user in front of a tool window they have to find.
 */
private class FigmaConnectPluginSuggestion(
  override val pluginIds: List<String>,
  private val offer: FigmaSuggestionOffer,
) : PluginSuggestion {

  override fun apply(fileEditor: FileEditor): EditorNotificationPanel {
    val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info)
    panel.text = offer.text

    // Where the platform logs its own `suggestion.shown` for this panel, so the two counts line up.
    offer.reportShown()

    panel.createActionLabel(offer.primaryActionText) { offer.accept() }
    panel.createActionLabel(offer.dismissActionText) { offer.dismiss() }

    return panel
  }
}

/**
 * The key [buildSuggestionIfNeeded] is given for its `suggestionDismissKey` parameter.
 *
 * This module owns the key and sets no value under it. The application-level guard that call opens
 * with passes, and the call answers the loaded-plugin question. [isFigmaSuggestionDismissed] is the
 * dismissal check, and it reads an answer recorded on one project, which an application-level guard
 * cannot express.
 */
private const val PLATFORM_GUARD_DISMISS_KEY: String = "markdown.figma.connect.advertiser.platform.guard"
