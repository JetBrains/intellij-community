// NEW_NAME: count
open class Base {
    open val hits: Int = 0
}

class Impl : Base() {
    override val hits<caret>: Int = 1
}
