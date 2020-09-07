package com.intellij.space.plugins.pipelines.services.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag

class SpaceRunTaskConfigurationOptions : LocatableRunConfigurationOptions() {
  @get:OptionTag("TASK_NAME")
  var taskName by string("")
}
