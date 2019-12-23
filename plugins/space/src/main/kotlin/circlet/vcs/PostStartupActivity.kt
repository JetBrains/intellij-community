package circlet.vcs

import com.intellij.openapi.project.*
import com.intellij.openapi.startup.*

class PostStartupActivity : StartupActivity {
    override fun runActivity(project: Project) {
        CircletProjectContext.getInstance(project) // init service
    }
}
