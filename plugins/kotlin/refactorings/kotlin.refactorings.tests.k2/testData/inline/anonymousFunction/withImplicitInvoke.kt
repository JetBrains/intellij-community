fun m() {
    (f<caret>un(): String {
        return "string"
    })().let {
        print(it)
    }
}