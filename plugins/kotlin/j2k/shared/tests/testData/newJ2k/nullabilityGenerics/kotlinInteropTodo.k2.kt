// ERROR: Return type mismatch: expected 'ArrayList<String?>', actual 'ArrayList<String>'.
// ERROR: Return type mismatch: expected 'ArrayList<String?>?', actual 'ArrayList<String>?'.
// ERROR: Argument type mismatch: actual type is 'ArrayList<String?>?', but 'ArrayList<String>?' was expected.
// ERROR: Argument type mismatch: actual type is 'ArrayList<String?>', but 'ArrayList<String>' was expected.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>?', actual 'ArrayList<String>?'.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>', actual 'ArrayList<String>'.
class Foo {
    fun testAssignment(k: K) {
        val l1 = k.return1()
        val l2: ArrayList<String?>? = k.return2()
        val l3 = k.return3()
        val l4: ArrayList<String?> = k.return4()
    }

    fun testArgument(
        k: K,
        l1: ArrayList<String?>?,
        l2: ArrayList<String?>?,
        l3: ArrayList<String?>,
        l4: ArrayList<String?>
    ) {
        k.argument1(l1)
        k.argument2(l2)
        k.argument3(l3)
        k.argument4(l4)
    }

    fun testReturn1(k: K): ArrayList<String?>? {
        return k.return1()
    }

    fun testReturn2(k: K): ArrayList<String?>? {
        return k.return2()
    }

    fun testReturn3(k: K): ArrayList<String?> {
        return k.return3()
    }

    fun testReturn4(k: K): ArrayList<String?> {
        return k.return4()
    }
}
