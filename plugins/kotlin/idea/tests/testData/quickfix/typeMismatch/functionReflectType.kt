// "Change type of 'myFunction' to 'KFunction2<Int, Int, Boolean>'" "true"
// WITH_STDLIB

fun foo() {
    var myFunction: (Int, Int) -> Int = <caret>::verifyData
}

fun verifyData(a: Int, b: Int) = (a > 10 && b > 10)