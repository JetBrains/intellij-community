// "Change type to '(String) -> [ERROR : Ay]'" "false"
// WITH_STDLIB
// ACTION: Add full qualifier
// ACTION: Change type of base property 'A.x' to '(Int) -> Int'
// ACTION: Go To Super Property
// ACTION: Introduce import alias
// ERROR: Type of 'x' is not a subtype of the overridden property 'public abstract val x: (String) -> [Error type: Unresolved type for Ay] defined in A'
// ERROR: Unresolved reference: Ay
// K2_AFTER_ERROR: PROPERTY_TYPE_MISMATCH_ON_OVERRIDE
// K2_AFTER_ERROR: UNRESOLVED_REFERENCE
// K2_ERROR: PROPERTY_TYPE_MISMATCH_ON_OVERRIDE
// K2_ERROR: UNRESOLVED_REFERENCE
interface A {
    val x: (String) -> Ay
}
interface B : A {
    override val x: (Int) -> Int<caret>
}