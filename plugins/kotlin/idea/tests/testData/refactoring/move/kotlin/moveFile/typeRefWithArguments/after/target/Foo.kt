package target

class OuterClass<T1> {
    inner class InnerClass<T2>

    class NestedClass<T3>
}

typealias OI<T1, T2> = OuterClass<T1>.InnerClass<T2> // (1)

typealias ON1<T1, T2> = OuterClass.NestedClass<T2> // (2)