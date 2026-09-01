package com.intellij.markdown.figmaAdvertiser

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.FUSEventSource
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

/**
 * What the Markdown advertisement for Figma Connect reports.
 *
 * Its own group rather than an extension of `figma.connect`: that group is declared inside the
 * plugin, which is absent exactly when this code runs, and FUS attributes a group to the plugin that
 * declares it. These events come from the Markdown plugin.
 *
 * The platform's `plugins.advertiser` group sees the same three moments as `suggestion.shown`,
 * `install.plugins` and `ignore.extensions`, and carries no field saying which trigger fired. Which
 * trigger converts is the question this advertisement exists to answer, so [TRIGGER] is the reason
 * this group exists. The install itself is the platform's to report and is not repeated here.
 */
@ApiStatus.Internal
object FigmaAdvertiserUsagesCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  const val GROUP_ID: String = "markdown.figma.advertiser"

  /** Bumped on any change to an event or a field of [GROUP_ID]. */
  const val GROUP_VERSION: Int = 1

  private val GROUP = EventLogGroup(GROUP_ID, GROUP_VERSION)

  /**
   * What made the offer appear.
   *
   * One value today, and a field rather than a property of the group, because the surfaces that
   * follow report their own triggers into groups of their own — they ship in other plugins, and FUS
   * attributes a group to the plugin that declares it. A query that reads the trigger reads the same
   * field name across all of them.
   */
  enum class SuggestionTrigger {
    /** A Markdown file whose text links to a Figma design. */
    MARKDOWN_FIGMA_LINK,
  }

  /**
   * Which surface carried the offer.
   *
   * The name is the [FUSEventSource] value the platform's `plugins.advertiser` group records for the
   * same clicks, so a query that joins the two groups reads one vocabulary.
   */
  enum class SuggestionSurface {
    /** The banner over an open file. */
    EDITOR;

    fun eventSource(): FUSEventSource = when (this) {
      EDITOR -> FUSEventSource.EDITOR
    }
  }

  private val TRIGGER = EventFields.Enum<SuggestionTrigger>("trigger")
  private val SURFACE = EventFields.Enum<SuggestionSurface>("surface")

  private val suggestionShown = GROUP.registerVarargEvent("suggestion.shown", TRIGGER, SURFACE)
  private val suggestionAccepted = GROUP.registerVarargEvent("suggestion.accepted", TRIGGER, SURFACE)
  private val suggestionDismissed = GROUP.registerVarargEvent("suggestion.dismissed", TRIGGER, SURFACE)

  /**
   * The offer is on screen.
   *
   * `PluginSuggestion.apply` runs per file editor and again on every
   * `EditorNotifications.updateAllNotifications()`, so an unguarded record would count repaints.
   * [ShownSuggestions] claims a shape once for the project, and the count is of projects that saw
   * the offer.
   */
  fun logSuggestionShown(project: Project, trigger: SuggestionTrigger, surface: SuggestionSurface) {
    if (!project.service<ShownSuggestions>().claim(trigger, surface)) return
    suggestionShown.log(project, fieldsOf(trigger, surface))
  }

  /**
   * The user asked for the plugin.
   *
   * The platform records the same click as `plugins.advertiser` `install.plugins`, and that record
   * says nothing about which trigger the user was answering.
   */
  fun logSuggestionAccepted(project: Project, trigger: SuggestionTrigger, surface: SuggestionSurface) {
    suggestionAccepted.log(project, fieldsOf(trigger, surface))
  }

  /**
   * The user closed the offer for good.
   *
   * The platform's `ignore.extensions` carries the surface and no plugin id, so a dismissal is
   * countable there only across every plugin at once. This event attributes one to this
   * advertisement.
   */
  fun logSuggestionDismissed(project: Project, trigger: SuggestionTrigger, surface: SuggestionSurface) {
    suggestionDismissed.log(project, fieldsOf(trigger, surface))
  }

  private fun fieldsOf(trigger: SuggestionTrigger, surface: SuggestionSurface): List<EventPair<*>> =
    listOf(TRIGGER.with(trigger), SURFACE.with(surface))
}

/**
 * Which shapes a project has already reported as shown.
 *
 * Per project, and not the application-wide set the platform keeps for its own `suggestion.shown`
 * (`PluginAdvertiserEditorNotificationProvider.kt:311`). That set answers "how many IDE runs saw
 * this banner". This group is registered to answer a per-project question, and an application-wide
 * set would report one showing while `suggestion.accepted` counted every project that acted.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ShownSuggestions {
  private val shown = ConcurrentHashMap.newKeySet<ShownSuggestion>()

  /** True the first time this project sees this shape, false every time after. */
  fun claim(
    trigger: FigmaAdvertiserUsagesCollector.SuggestionTrigger,
    surface: FigmaAdvertiserUsagesCollector.SuggestionSurface,
  ): Boolean = shown.add(ShownSuggestion(trigger, surface))

  private data class ShownSuggestion(
    val trigger: FigmaAdvertiserUsagesCollector.SuggestionTrigger,
    val surface: FigmaAdvertiserUsagesCollector.SuggestionSurface,
  )
}
