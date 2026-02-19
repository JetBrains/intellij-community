fun main() {
    val list = listOf(1, 2, 3)
    list.filter { it > 2 }
}

public inline fun <M> Iterable<M>.filter(predicate: (M) -> Boolean): List<M> {
    //Breakpoint!
    return filterTo(ArrayList<M>(), predicate)
}

// EXPRESSION: map { "" }
// RESULT: instance of java.util.ArrayList(id=ID): Ljava/util/ArrayList;

// EXPRESSION: this.map { "" }
// RESULT: instance of java.util.ArrayList(id=ID): Ljava/util/ArrayList;

// EXPRESSION: map<M, String> { "" }
// RESULT: instance of java.util.ArrayList(id=ID): Ljava/util/ArrayList;

// EXPRESSION: map { it: M -> "" }
// RESULT: instance of java.util.ArrayList(id=ID): Ljava/util/ArrayList;

