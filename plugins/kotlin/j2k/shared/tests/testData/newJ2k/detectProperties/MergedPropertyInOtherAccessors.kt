class Editor {
    val state: State = State()

    var isScrollToCaret: Boolean
        get() = state.isScrollToCaret
        set(scrollToCaret) {
            state.isScrollToCaret = scrollToCaret
        }
}
