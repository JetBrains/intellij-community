package circlet.reviews

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.*
import circlet.settings.*
import com.intellij.openapi.project.*
import kotlinx.coroutines.experimental.*
import runtime.*
import runtime.reactive.*
import javax.swing.*

class CircletReviewsForm(private val project: Project, override val lifetime: Lifetime) :
    Lifetimed {

    lateinit var panel: JPanel
        private set

    fun reload() {
        launch(UiDispatch.coroutineContext) {
            val reviews = project.connection.loginModel?.clientOrNull?.codeReview?.listReviews(
                BatchInfo(null, 30), ProjectKey(project.settings.projectKey.value), false
            )?.data ?: return@launch

            reload(reviews)
        }
    }

    private fun reload(reviews: List<CodeReviewShortInfo>) {
        println("reviews = $reviews")
    }
}
