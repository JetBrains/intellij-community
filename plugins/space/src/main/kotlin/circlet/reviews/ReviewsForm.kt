package circlet.reviews

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.*
import circlet.runtime.*
import circlet.settings.*
import com.intellij.openapi.project.*
import com.intellij.ui.components.*
import klogging.*
import kotlinx.coroutines.experimental.*
import runtime.async.*
import runtime.reactive.*
import runtime.utils.*
import javax.swing.*

private val LOG = KLoggers.logger("circlet.reviews.ReviewsFormKt")

class ReviewsForm(private val project: Project, parentLifetime: Lifetime) :
    Lifetimed by NestedLifetimed(parentLifetime) {

    lateinit var panel: JPanel
        private set

    private lateinit var list: JBList<CodeReviewWithCount>
    private val model = ReviewsListModel()

    private val reloader = updater<Unit>("Reviews Reloader") {
        reloadImpl()
    }

    init {
        list.model = model
    }

    fun reload() {
        reloader.offer()
    }

    private suspend fun reloadImpl() {
        val reviews = project.clientOrNull?.codeReview?.listReviews(
            BatchInfo(null, 30), ProjectKey(project.settings.projectKey.value), null, null, null, null, null, ReviewSorting.CreatedAtDesc
        )?.data ?: return

        reload(reviews)
    }

    private fun reload(reviews: List<CodeReviewWithCount>) {
        model.elements = reviews
    }
}

private fun <T> Lifetimed.updater(name: String, update: suspend (T) -> Unit): Channel<T> {
    val channel = boundedChannel<T>(name, 0, lifetime)

    launch(ApplicationUiDispatch.contextWithExplicitLog, start = CoroutineStart.UNDISPATCHED) {
        channel.forEach {
            try {
                update(it)
            }
            catch (t: Throwable) {
                LOG.error(t) { "Updater '$name' error" } // TODO
            }
        }
    }

    return channel
}

private fun Channel<Unit>.offer() {
    offer(Unit)
}
