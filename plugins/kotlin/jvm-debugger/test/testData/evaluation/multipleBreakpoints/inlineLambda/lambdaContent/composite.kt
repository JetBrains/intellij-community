// IGNORE_K1

fun main() {
    foo { it ->

        fun localFunWithLocalClass(p: Int): Int {
            class X(val x: Int)
            return X(p).x
        }

        fun createFunWithLocalObj(): () -> Int {
            return {
                object {
                    val x = 1
                }.x
            }
        }

        val complexLambda = object {
            val prop = {

                var x = 10
                x += 100

                val y = localFunWithLocalClass(1000)

                val z = createFunWithLocalObj()()

                val l = { 10000 }

                x + y + z + l()
            }
        }.prop

        complexLambda() + it
    }
}

inline fun foo(block: (Int) -> Int) {
    // EXPRESSION: block(100000)
    // RESULT: 111111: I
    //Breakpoint!
    val x = 1
}