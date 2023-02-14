import java.util.stream.Stream

fun foo() {
    Stream.empty<String>().filter { it.isEmpty() }
}

fun doSelectItem(selectItemFunction: () -> Unit) {
    selectItemFunction()
    val baz = fun() {
        Local()
    }
    baz()
}

fun lambdaInPlaceCall() {
    while ({ true }()) {

    }
}

var lambdaVarWithType: (String) -> Int = { it.length }
