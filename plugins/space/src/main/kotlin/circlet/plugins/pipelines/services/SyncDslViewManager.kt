package circlet.plugins.pipelines.services

import com.intellij.build.*
import com.intellij.openapi.project.*

class SyncDslViewManager(project: Project, buildContentManager: BuildContentManager) : AbstractViewManager(project, buildContentManager) {
    public override fun getViewName(): String {
        return "CircletAutoDSL"
    }
}
