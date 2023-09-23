enum class JavaEnum {
    A("a"), B;

    constructor(x: String) {
        this.x = x
    }

    constructor() {
        this.x = "default"
    }

    protected var x: String
}
