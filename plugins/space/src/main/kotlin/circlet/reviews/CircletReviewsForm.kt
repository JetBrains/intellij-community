package circlet.reviews

import com.intellij.openapi.project.*
import runtime.reactive.*
import javax.swing.*

class CircletReviewsForm(@Suppress("unused") private val project: Project, override val lifetime: Lifetime) :
    Lifetimed {

    lateinit var panel: JPanel
        private set
}
