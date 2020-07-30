package circlet.vcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

open class PostStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    CircletProjectContext.getInstance(project) // init service
  }
}
