package org.intellij.plugins.markdown

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerType
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object MarkdownUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("markdown.events", 2)

  val RUNNER_EXECUTED = GROUP.registerEvent(
    "runner.executed",
    EventFields.Enum("location", RunnerPlace::class.java),
    EventFields.Enum("type", RunnerType::class.java),
    EventFields.Class("runner")
  )

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}