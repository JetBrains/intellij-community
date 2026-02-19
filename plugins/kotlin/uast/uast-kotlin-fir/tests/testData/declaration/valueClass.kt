@kotlin.jvm.JvmInline
value class AnchorType internal constructor(internal val ratio: Float) {
    companion object {
        val Start = AnchorType(0f)
        val Center = AnchorType(0.5f)
        val End = AnchorType(1f)
    }
}

class User(
    val p : AnchorType,
    var q : AnchorType,
) {
    fun foo() = p
    fun bar(): () -> AnchorType = { foo() }
}

class Alignment(val horizontal: Horizontal, val vertical: Vertical) {
    @kotlin.jvm.JvmInline
    value class Horizontal private constructor(private val value: Int) {
        companion object {
            val Start: Horizontal = Horizontal(0)
            val CenterHorizontally: Horizontal = Horizontal(1)
            val End: Horizontal = Horizontal(2)
        }
    }

    @kotlin.jvm.JvmInline
    value class Vertical private constructor(private val value: Int) {
        companion object {
            val Top: Vertical = Vertical(0)
            val CenterVertically: Vertical = Vertical(1)
            val Bottom: Vertical = Vertical(2)
        }
    }

    companion object {
        val TopStart: Alignment = Alignment(Horizontal.Start, Vertical.Top)
        val Top: Vertical = Vertical.Top
        val Start: Horizontal = Horizontal.Start
    }
}