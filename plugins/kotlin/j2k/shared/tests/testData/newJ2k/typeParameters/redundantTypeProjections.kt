import java.util.Arrays

class J {
    interface Element

    inner class Box<T>(var t: T) {
        fun putT(t: T) {
            this.t = t
        }
    }

    inner class Container(vararg children: Element?) : Element {
        val children: List<Element> = ArrayList(Arrays.asList(*children))
        private val containerChildren: List<Container> = ArrayList()

        private fun testAssignment1(elements: List<Element>) {
            var elements: List<Element>? = elements
            elements = children
        }

        private fun mergeIntoParameter(elements: MutableList<in Element>) {
            elements.addAll(containerChildren)
        }

        val box: Box<out Container>
            get() {
                val box: Box<Container> = Box<Container>(Container())
                return box
            }
    }
}
