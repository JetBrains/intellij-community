import java.util.Arrays

class J {
    interface Element

    class Container(vararg children: Element?) : Element {
        val children: List<Element> = ArrayList(Arrays.asList(*children))
    }
}
