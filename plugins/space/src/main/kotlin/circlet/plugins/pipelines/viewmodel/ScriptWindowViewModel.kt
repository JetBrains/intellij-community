package circlet.plugins.pipelines.viewmodel

import circlet.pipelines.config.api.*
import circlet.plugins.pipelines.services.*
import circlet.runtime.*
import com.intellij.openapi.project.*
import runtime.async.*
import runtime.reactive.*

class ScriptWindowViewModel(private val lifetime: Lifetime, private val project: Project) {
    val script = PropertyImpl<ScriptViewModel?>(null)
}


class ScriptViewModel(
    private val lifetime: Lifetime,
    val config: ProjectConfig) {
}


fun createEmptyScriptViewModel(lifetime: Lifetime) : ScriptViewModel {
    return ScriptViewModel(lifetime, ProjectConfig(emptyList(), emptyList(), emptyList()))

}

