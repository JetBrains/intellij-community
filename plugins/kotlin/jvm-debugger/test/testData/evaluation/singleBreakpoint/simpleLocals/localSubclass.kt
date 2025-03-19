package localSubclass

fun main() {
    class F : CharSequence {
        override val length: Int
            get() = 1

        override fun get(index: Int): Char {
            return '1'
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return "1"
        }
    }
    val f: CharSequence
    f = F()
    //Breakpoint!
    f
}

// EXPRESSION: f
// RESULT: instance of localSubclass.LocalSubclassKt$main$F(id=ID): LlocalSubclass/LocalSubclassKt$main$F;
