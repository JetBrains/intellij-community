package foo

private val bar = "bar"

fun test() {
    ba<caret>r
}

// REF: (TopLevelPropertyNameCollision.kt in foo).bar