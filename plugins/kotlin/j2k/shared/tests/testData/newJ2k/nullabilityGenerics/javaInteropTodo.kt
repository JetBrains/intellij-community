// ERROR: Type mismatch: inferred type is kotlin.collections.ArrayList<String?>? /* = java.util.ArrayList<String?>? */ but java.util.ArrayList<String>? was expected
// ERROR: Type mismatch: inferred type is kotlin.collections.ArrayList<String?> /* = java.util.ArrayList<String?> */ but java.util.ArrayList<String> was expected
class Foo {
    fun testAssignment(j: J) {
        val l1 = j.return1()
        val l2 = j.return2()
        val l3 = j.return3()
        val l4 = j.return4()

        val l5 = j.field1
        val l6 = j.field2
        val l7 = j.field3
        val l8 = j.field4
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

    fun testReturn2(j: J): ArrayList<String>? {
        return j.return2()
    }

    fun testReturn3(j: J): ArrayList<String?> {
        return j.return3()
    }

    fun testReturn4(j: J): ArrayList<String> {
        return j.return4()
    }
}
