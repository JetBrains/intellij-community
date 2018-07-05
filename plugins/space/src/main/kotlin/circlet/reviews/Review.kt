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

fun CodeReviewWithCount.toReview(): Review =
    Review(
        id = review.id,
        title = review.title,
        createdAt = review.createdAt.toLocalDateTime(),
        createdBy = review.createdBy.resolve()
    )
