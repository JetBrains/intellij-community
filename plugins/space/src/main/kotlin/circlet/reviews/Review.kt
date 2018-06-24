package circlet.reviews

import circlet.client.api.*
import circlet.platform.api.*
import circlet.platform.client.*
import org.joda.time.*

data class Review(
    val id: Int,
    val title: String,
    val createdBy: TD_MemberProfile,
    val timestamp: DateTime
)

fun CodeReviewWithCount.toReview(client: KCircletClient): Review =
    Review(
        id = review.id,
        title = review.title,
        createdBy = review.createdBy.resolve(),
        timestamp = DateTime(review.timestamp)
    )
