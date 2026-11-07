import java.util.Arrays

class J {
    interface Element

    class Container(vararg children: Element?) : Element {
        private val children: MutableList<Element?>

        init {
            this.children = ArrayList<Element?>(Arrays.asList<Element?>(*children))
        }

        fun getChildren(): MutableList<out Element?> {
            return children
        }
    }
}
