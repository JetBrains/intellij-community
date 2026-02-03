class Foo<caret>(param: String){
    val x = param.uppercase()

    init {
        val z = param.uppercase()
    }
}

// IGNORE_K1