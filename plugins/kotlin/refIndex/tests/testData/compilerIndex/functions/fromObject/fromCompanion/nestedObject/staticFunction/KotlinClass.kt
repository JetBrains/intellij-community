package one.two

class KotlinClass {
    companion object {
        object NestedObject {
            @JvmStatic
            fun staticFunc<caret>tion() {

            }
        }
    }
}

class Receiver