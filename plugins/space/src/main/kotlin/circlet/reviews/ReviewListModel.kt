package circlet.reviews

import javax.swing.*

class ReviewListModel : AbstractListModel<Review>() {
    var elements: List<Review> = listOf()
        set(value) {
            fireIntervalRemoved(this, 0, field.size)

            field = value

            fireIntervalAdded(this, 0, field.size)
        }

    override fun getElementAt(index: Int): Review = elements[index]

    override fun getSize(): Int = elements.size
}
