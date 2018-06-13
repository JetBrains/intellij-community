package circlet.tools

import circlet.components.*
import circlet.reviews.*
import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*

class ReviewsToolWindowPanel(project: Project) :
    SimpleToolWindowPanel(false, true), LifetimedDisposable by SimpleLifetimedDisposable() {

    private val form = ReviewsForm(project, lifetime)

    init {
        setContent(form.panel)

        project.connection.connected.forEach(lifetime) {
            if (project.toolWindow?.isVisible == true) {
                reload()
            }
        }
    }

    fun reload() {
        form.reload()
    }
}
