package circlet.reviews

import circlet.client.api.*
import circlet.platform.client.*
import circlet.utils.*
import java.time.*

data class Review(
    val id: Int,
    val title: String,
    val createdAt: LocalDateTime,
    val createdBy: TD_MemberProfile
)

fun CodeReviewWithCount.toReview(): Review {
    return Review(
        id = review.resolve().number,
        title = review.resolve().title,
        createdAt = review.resolve().createdAt.toLocalDateTime(),
        createdBy = review.resolve().createdBy.resolve()
    )
}
