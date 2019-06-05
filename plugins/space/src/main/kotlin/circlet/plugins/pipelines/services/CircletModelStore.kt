package circlet.plugins.pipelines.services

import circlet.plugins.pipelines.viewmodel.*
import circlet.utils.*
import com.intellij.openapi.project.*

class CircletModelStore(val project: Project): LifetimedComponent by SimpleLifetimedComponent() {
    val viewModel = ScriptWindowViewModel(lifetime, project)
}
