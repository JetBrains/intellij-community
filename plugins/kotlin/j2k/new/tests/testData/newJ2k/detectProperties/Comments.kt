internal class A(// comment for field2 setter
    // comment for field2 getter
    var field2 // comment for field2
    : Int
) {
    /**
     * Comment for field1 setter
     */
    // Comment for field1 getter
    // Comment for field1
    var field1 = 0

    // comment for field3 setter
    // comment for field3 getter
    // comment before field3
    var field3 = 0 // comment for field3

    var property: Int
        // comment for getProperty
        get() = 1 // end of getProperty
        // comment for setProperty
        set(value) {} // end of setProperty
}
