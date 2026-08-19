fun testWhileBreak() {
    while (true) {
        break
    }
}

fun testWhileContinue() {
    while (true) {
        continue
    }
}

fun testDoWhileBreak() {
    do {
        break
    } while (true)
}

fun testDoWhileContinue() {
    do {
        continue
    } while (true)
}

fun testForBreak() {
    for (i in 0..10) {
        break
    }
}

fun testForContinue() {
    for (i in 0..10) {
        continue
    }
}

fun testLabeledBreak() {
    outer@ while (true) {
        inner@ for (i in 0..10) {
            break@outer
        }
    }
}

fun testLabeledContinue() {
    outer@ for (i in 0..10) {
        inner@ while (true) {
            continue@outer
        }
    }
}

fun testNestedBreak() {
    while (true) {
        for (i in 0..10) {
            break
        }
    }
}

fun testNestedContinue() {
    for (i in 0..10) {
        while (true) {
            continue
        }
    }
}

fun testBreakContinueInWhen() {
    outer@ while(true) {
        mid@ for(i in 0..10) {
            when (val x = 1) {
                1 -> break
                2 -> continue
                3 -> break@outer
                4 -> continue@outer
                5 -> break@mid
                6 -> continue@mid
            }
            inner@ while (true) {
                when (val x = 1) {
                    1 -> break
                    2 -> continue
                    3 -> break@outer
                    4 -> continue@outer
                    5 -> break@mid
                    6 -> continue@mid
                    7 -> break@inner
                    8 -> continue@inner
                }
            }
        }
    }
}
