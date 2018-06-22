package circlet.reviews

import circlet.app.*
import circlet.client.*
import circlet.client.api.*
import circlet.platform.client.*
import org.joda.time.*

data class Review(
    val id: Int,
    val title: String,
    val createdBy: String,
    val timestamp: DateTime
)

fun CodeReviewWithCount.toReview(loginModel: LoginModel, client: KCircletClient): Review =
    Review(
        id = review.id,
        title = review.title,
        createdBy = review.createdBy.safeResolve(client)?.fullname(loginModel.me?.preferredLanguage) ?: "",
        timestamp = DateTime(review.timestamp)
    )
