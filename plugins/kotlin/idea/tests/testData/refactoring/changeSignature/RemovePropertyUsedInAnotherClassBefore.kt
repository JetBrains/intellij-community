class Foo<caret>(val param: String)
fun m(f: Foo) {
    val p = f.param
}

// IGNORE_K1