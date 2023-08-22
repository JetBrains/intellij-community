package genericClass

class HolderUnbound<T>(val value: T) {
    fun foo() {
        // EXPRESSION: value
        // RESULT: instance of java.lang.Integer(id=ID): Ljava/lang/Integer;
        //Breakpoint!
        value
    }
}

class HolderBound<T : CharSequence>(val value: T) {
    fun foo() {
        // EXPRESSION: value
        // RESULT: "Hello!": Ljava/lang/String;
        //Breakpoint!
        value
    }
}

class HolderGenericBound<T, C : Collection<T>>(val collection: C) {
    fun foo() {
        // EXPRESSION: collection.size
        // RESULT: 2: I
        //Breakpoint!
        collection
    }
}

class HolderPair<S, T>(val pair: Pair<S, T>) {
    fun foo() {
        // EXPRESSION: (pair.first as CharSequence).length + (pair.second as Int)
        // RESULT: 47: I
        //Breakpoint!
        pair
    }
}

class HolderFunction<S, T, U, R>(val function: (S, T, U) -> R) {
    fun foo(s: S, t: T, u: U) {
        // EXPRESSION: function(s, t, u)
        // RESULT: "The answer is 42": Ljava/lang/String;
        //Breakpoint!
        function
    }
}

class HolderWithGenericFunction<T>(val value1: T) {
    fun <S> foo(value2: S) {
        // EXPRESSION: value1.toString() + " " + value2.toString()
        // RESULT: "Hello there": Ljava/lang/String;
        //Breakpoint!
        value1.toString() + value2.toString()
    }
}

class HolderNullable<T : Any>(val value: T?) {
    fun foo() {
        // EXPRESSION: value
        // RESULT: "Not null actually": Ljava/lang/String;
        //Breakpoint!
        value
    }
}

interface I1
interface I2
class C : I1, I2

class HolderMultiBound<T>(val value: T) where T : I1, T : I2 {
    fun foo() {
        // EXPRESSION: value
        // RESULT: instance of genericClass.C(id=ID): LgenericClass/C;
        //Breakpoint!
        value
    }
}

class HolderNestedGeneric<T>(val collection: List<List<T>>) {
    fun foo() {
        // EXPRESSION: collection.first().size
        // RESULT: 3: I
        //Breakpoint!
        collection
    }
}

class HolderNestedBound<T, C : List<List<T>>>(val collection: C) {
    fun foo() {
        // EXPRESSION: collection.first()[2]
        // RESULT: "baz": Ljava/lang/String;
        //Breakpoint!
        collection
    }
}

fun main() {
    val holderUnbound = HolderUnbound(1)
    holderUnbound.foo()

    val holderBound = HolderBound("Hello!")
    holderBound.foo()

    val holderGenericBound = HolderGenericBound(listOf(42, 81))
    holderGenericBound.foo()

    val holderPair = HolderPair<Any, Any>("Hello" to 42)
    holderPair.foo()

    val holderFunction = HolderFunction<String, Double, List<Long>, String>({ s, d, l ->
                                                                                s + " " + d.toInt() + l.size
                                                                            })
    holderFunction.foo("The answer is", 4.0, listOf(1L, 2L))

    val holderWithGenericFunction = HolderWithGenericFunction("Hello")
    holderWithGenericFunction.foo("there")

    val holderNullable = HolderNullable("Not null actually")
    holderNullable.foo()

    val holderMultiBound = HolderMultiBound(C())
    holderMultiBound.foo()

    val holderNestedGeneric = HolderNestedGeneric(listOf(listOf(1, 2, 3)))
    holderNestedGeneric.foo()

    val holderNestedBound = HolderNestedBound(listOf(listOf("foo", "bar", "baz", "ban")))
    holderNestedBound.foo()
}
