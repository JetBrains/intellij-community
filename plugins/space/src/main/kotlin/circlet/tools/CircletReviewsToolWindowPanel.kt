package circlet.tools

import circlet.reviews.*
import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*

class CircletReviewsToolWindowPanel(project: Project) :
    SimpleToolWindowPanel(false, true), LifetimedDisposable by SimpleLifetimedDisposable() {

    init {
        val form = CircletReviewsForm(project, lifetime)

        setContent(form.panel)
    }
}
