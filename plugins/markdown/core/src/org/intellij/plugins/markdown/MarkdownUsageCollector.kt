package org.intellij.plugins.markdown

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerPlace
import org.intellij.plugins.markdown.extensions.jcef.commandRunner.RunnerType

class MarkdownUsageCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("markdown.events", 1)

    @JvmField
    val RUNNER_EXECUTED = GROUP.registerEvent(
      "runner.executed",
      EventFields.Enum("place", RunnerPlace::class.java),
      EventFields.Enum("type", RunnerType::class.java),
      EventFields.Class("runner")
    )

  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}