package circlet.tools

import com.intellij.openapi.*
import com.intellij.openapi.project.*
import com.intellij.openapi.ui.*

class CircletReviewsToolWindowPanel(@Suppress("unused") private val project: Project) :
    SimpleToolWindowPanel(false, true), Disposable
{
    override fun dispose() {
    }
}
