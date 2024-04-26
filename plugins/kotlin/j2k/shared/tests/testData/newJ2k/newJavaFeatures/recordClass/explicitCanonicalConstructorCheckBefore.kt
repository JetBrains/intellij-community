// JVM_TARGET: 17
class R(x: Int) {
    val x: Int

    init {
        if (x <= 0) throw RuntimeException()
        this.x = x
    }
}
