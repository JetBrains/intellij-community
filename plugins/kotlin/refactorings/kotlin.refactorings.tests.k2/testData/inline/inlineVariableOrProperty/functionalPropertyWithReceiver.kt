object Main {
    val funVal0 = {
        print(42)
        "lambdaInitialization"
    }
}

fun main() {
    Main.<caret>funVal0()
}