fun main(args : Array<String>) {
    for (i in 1..100) {
        when {
            i%3 == 0 -> {print("Fizz"); continue;}
            i%5 == 0 -> {print("Buzz"); continue;}

            (i%3 != 0 && i%5 != 0) -> {print(i); continue;}
            else -> println()
        }
    }
}

fun foo() : Unit {
    println() {
        println()


        <selection>pr<caret>intln()</selection>
        println()
    }

    println(array(1, 2, 3))
    println()
}