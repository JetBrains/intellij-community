package circlet.reviews

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.*
import circlet.settings.*
import com.intellij.openapi.project.*
import kotlinx.coroutines.experimental.*
import runtime.*
import runtime.async.*
import runtime.reactive.*
import javax.swing.*

class CircletReviewsForm(private val project: Project, override val lifetime: Lifetime) :
    Lifetimed {

    lateinit var panel: JPanel
        private set

    private val reloader = updater<Unit>("Reviews Reloader") {
        reloadImpl()
    }

    fun reload() {
        reloader.offer()
    }

    private suspend fun reloadImpl() {
        val reviews = project.clientOrNull?.codeReview?.listReviews(
            BatchInfo(null, 30), ProjectKey(project.settings.projectKey.value), false
        )?.data ?: return

        reload(reviews)
    }

    private fun reload(reviews: List<CodeReviewShortInfo>) {
        println("reviews = $reviews")
    }
}

private fun <T> Lifetimed.updater(name: String, update: suspend (T) -> Unit): Channel<T> {
    val channel = boundedChannel<T>(name, 0, lifetime)

    launch(UiDispatch.coroutineContext, start = CoroutineStart.UNDISPATCHED) {
        channel.forEach {
            update(it)
        }
    }

    return channel
}

private fun Channel<Unit>.offer() {
    offer(Unit)
}
