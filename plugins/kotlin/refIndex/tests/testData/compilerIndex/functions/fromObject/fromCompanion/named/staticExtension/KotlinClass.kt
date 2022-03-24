package one.two

class KotlinClass {
    companion object Named {
        @JvmStatic
        fun Receiver.staticExt<caret>ension(i: Int) {

        }
    }
}

class Receiver