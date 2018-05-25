package circlet.tools

import circlet.reviews.*
import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*

class CircletReviewsToolWindowPanel(project: Project) :
    SimpleToolWindowPanel(false, true), LifetimedDisposable by SimpleLifetimedDisposable() {

    private val form = CircletReviewsForm(project, lifetime)

    init {
        setContent(form.panel)
    }

    fun reload() {
        form.reload()
    }
}
