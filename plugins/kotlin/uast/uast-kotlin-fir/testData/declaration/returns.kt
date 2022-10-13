fun foo() {
    run {
        return@foo
    }
}

fun lambdaWithLabel() {
    listOf(1, 2, 3, 4).forEach l@{
        if (it % 2 == 0) return@l
        print(it)
    }
}

fun implicitLabel() {
    listOf(1, 2, 3, 4).forEach {
        if (it % 2 == 0) return@forEach
        print(it)
    }
}

fun simulateBreak() {
    run loop@{
      listOf(1, 2, 3, 4).forEach {
        if (it == 3) return@loop
        print(it)
      }
    }
}
