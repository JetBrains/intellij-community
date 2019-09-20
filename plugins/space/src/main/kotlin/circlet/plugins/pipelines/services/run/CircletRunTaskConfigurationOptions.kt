package circlet.plugins.pipelines.services.run

import com.intellij.execution.configurations.*
import com.intellij.util.xmlb.annotations.*

class CircletRunTaskConfigurationOptions : LocatableRunConfigurationOptions() {
    @get:OptionTag("TASK_NAME")
    var taskName =""
}
