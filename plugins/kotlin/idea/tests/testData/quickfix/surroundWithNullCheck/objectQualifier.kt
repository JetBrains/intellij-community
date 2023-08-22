// "Surround with null check" "true"

object Obj {
    fun foo(x: Int) = x
}

fun use(arg: Int?) {
    Obj.foo(<caret>arg)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.SurroundWithNullCheckFix