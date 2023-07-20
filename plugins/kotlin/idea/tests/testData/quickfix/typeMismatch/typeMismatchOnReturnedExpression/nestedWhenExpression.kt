// "Change return type of enclosing function 'test' to 'Any'" "true"
class O
class P

fun test(b: Boolean): O =
    when {
        b -> O()
        else -> when<caret> {
            true -> O(); else -> P()
        }
    }
