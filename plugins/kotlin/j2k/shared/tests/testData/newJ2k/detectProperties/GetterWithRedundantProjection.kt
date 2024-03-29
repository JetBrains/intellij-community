import java.util.Arrays

class J {
    interface Element

    inner class Box<T>(var t: T) {
        fun putT(t: T) {
            this.t = t
        }
    }

    class Container(vararg children: Element?) : Element {
        val children: List<Element> = ArrayList(Arrays.asList(*children))
        private val containerChildren: List<in Container> = ArrayList<Container>()

        private fun testAssignment1(elements: List<in Element>) {
            var elements: List<in Element>? = elements
            elements = children
        }

        private fun testAssignment2(elements: List<in Element>) {
            var elements: List<in Element>? = elements
            elements = containerChildren
        }

        fun mergeWithChildren(other: MutableList<in Element>): List<in Container> {
            other.addAll(children)
            return other
        }

        val box: Box<out Container>
            get() {
                val box: Box<Container> = Box<Any?>(Container())
                return box
            }
    }
}
