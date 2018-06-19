package circlet.reviews

import circlet.client.api.*
import javax.swing.*

class ReviewsListModel : AbstractListModel<CodeReviewWithCount>() {
    var elements: List<CodeReviewWithCount> = listOf()
        set(value) {
            fireIntervalRemoved(this, 0, field.size)

            field = value

            fireIntervalAdded(this, 0, field.size)
        }

    override fun getElementAt(index: Int): CodeReviewWithCount = elements[index]

    override fun getSize(): Int = elements.size
}
