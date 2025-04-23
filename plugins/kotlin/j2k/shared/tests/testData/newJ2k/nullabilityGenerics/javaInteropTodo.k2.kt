// ERROR: Return type mismatch: expected 'ArrayList<String?>', actual '@NotNull() ArrayList<@NotNull() String>'.
// ERROR: Return type mismatch: expected 'ArrayList<String?>?', actual '@Nullable() ArrayList<@NotNull() String>?'.
// ERROR: Argument type mismatch: actual type is 'ArrayList<String?>?', but '@Nullable() ArrayList<@NotNull() String>?' was expected.
// ERROR: Argument type mismatch: actual type is 'ArrayList<String?>', but '@NotNull() ArrayList<@NotNull() String>' was expected.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>?', actual '@Nullable() ArrayList<@NotNull() String>?'.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>', actual '@NotNull() ArrayList<@NotNull() String>'.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>?', actual '@Nullable() ArrayList<@NotNull() String>?'.
// ERROR: Initializer type mismatch: expected 'ArrayList<String?>', actual '@NotNull() ArrayList<@NotNull() String>'.
class Foo {
    fun testAssignment(j: J) {
        val l1 = j.return1()
        val l2: ArrayList<String?>? = j.return2()
        val l3 = j.return3()
        val l4: ArrayList<String?> = j.return4()

        val l5 = j.field1
        val l6: ArrayList<String?>? = j.field2
        val l7 = j.field3
        val l8: ArrayList<String?> = j.field4
    }

    fun testArgument(
        j: J,
        l1: ArrayList<String?>?,
        l2: ArrayList<String?>?,
        l3: ArrayList<String?>,
        l4: ArrayList<String?>
    ) {
        j.argument1(l1)
        j.argument2(l2)
        j.argument3(l3)
        j.argument4(l4)
    }

    fun testReturn1(j: J): ArrayList<String?>? {
        return j.return1()
    }

    fun testReturn2(j: J): ArrayList<String?>? {
        return j.return2()
    }

    fun testReturn3(j: J): ArrayList<String?> {
        return j.return3()
    }

    fun testReturn4(j: J): ArrayList<String?> {
        return j.return4()
    }
}
