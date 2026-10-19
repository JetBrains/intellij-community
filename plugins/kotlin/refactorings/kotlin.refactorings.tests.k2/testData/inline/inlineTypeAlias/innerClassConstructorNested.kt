class Root {
    inner class Middle {
        inner class Deep(val value: Int)
    }
}

typealias <caret>DeepAlias = Root.Middle.Deep

fun use(root: Root) {
    root.Middle().DeepAlias(1)
}
