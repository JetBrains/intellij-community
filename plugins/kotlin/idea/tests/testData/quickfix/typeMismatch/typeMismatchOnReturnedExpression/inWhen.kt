// "Change return type of enclosing function 'test' to 'Any?'" "true"
class O
class P

fun test(b: Boolean, p: P?): O {
    return when {
        b -> O()
        else -> p<caret>
    }
}