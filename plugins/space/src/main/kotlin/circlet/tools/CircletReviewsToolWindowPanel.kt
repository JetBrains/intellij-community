package circlet.tools

import circlet.utils.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*

class CircletReviewsToolWindowPanel(@Suppress("unused") private val project: Project) :
    SimpleToolWindowPanel(false, true), LifetimedDisposable by SimpleLifetimedDisposable()
