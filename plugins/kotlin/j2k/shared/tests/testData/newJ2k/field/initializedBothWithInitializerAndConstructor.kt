class TestFieldInitializer(string: String?) {
    var string: String? = ""
        private set

    init {
        this.string = string
    }
}
