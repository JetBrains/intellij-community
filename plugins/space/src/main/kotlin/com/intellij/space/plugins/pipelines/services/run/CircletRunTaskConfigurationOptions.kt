package circlet.plugins.pipelines.services.run

import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.util.xmlb.annotations.OptionTag

class CircletRunTaskConfigurationOptions : LocatableRunConfigurationOptions() {
  @get:OptionTag("TASK_NAME")
  var taskName by string("")
}
