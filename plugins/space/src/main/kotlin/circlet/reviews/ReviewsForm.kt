package circlet.reviews

import circlet.client.*
import circlet.client.api.*
import circlet.components.*
import circlet.platform.api.*
import circlet.runtime.*
import circlet.settings.*
import circlet.ui.*
import com.intellij.openapi.project.*
import com.intellij.uiDesigner.core.*
import klogging.*
import kotlinx.coroutines.experimental.*
import runtime.async.*
import runtime.reactive.*
import runtime.utils.*
import javax.swing.*

private val LOG = KLoggers.logger("circlet.reviews.ReviewsFormKt")

class ReviewsForm(private val project: Project, parentLifetime: Lifetime) :
    Lifetimed by NestedLifetimed(parentLifetime) {

    val panel = JPanel(GridLayoutManager(1, 1))

    private val list = JComponentBasedList(lifetime)

    private val reloader = updater<Unit>("Reviews Reloader") {
        reloadImpl()
    }

    init {
        panel.add(
            list.component,
            GridConstraints().apply {
                row = 0
                column = 0
                fill = GridConstraints.FILL_BOTH
            }
        )
    }

    fun reload() {
        reloader.offer()
    }

    private suspend fun reloadImpl() {
        project.clientOrNull?.let { client ->
            val reviews = client.codeReview.listReviews(
                BatchInfo(null, 30), ProjectKey(project.settings.projectKey.value), null,
                null, null, null, null, ReviewSorting.CreatedAtDesc
            ).data.map(CodeReviewWithCount::toReview)

            reload(reviews)
        }
    }

    private fun reload(reviews: List<Review>) {
        list.removeAll()

        val preferredLanguage = project.connection.loginModel?.me?.preferredLanguage

        reviews.forEach { review -> list.add(ReviewListItem(review, preferredLanguage)) }

        list.revalidate()
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
