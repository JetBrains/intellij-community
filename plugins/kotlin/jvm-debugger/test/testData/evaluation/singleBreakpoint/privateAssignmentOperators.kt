class ClassWithMemberInc(private var field: Int) {
    private operator fun inc(): ClassWithMemberInc {
        return ClassWithMemberInc(field + 1)
    }
}

class ClassWithExtensionInc(var field: Int)

private operator fun ClassWithExtensionInc.inc(): ClassWithExtensionInc {
    return ClassWithExtensionInc(field + 1)
}

class ClassWithMemberPlusAssign(private var field: Int) {
    private operator fun plusAssign(classX: ClassWithMemberPlusAssign) {
        field += classX.field
    }
}

class ClassWithExtensionPlusAssign(var field: Int)

private operator fun ClassWithExtensionPlusAssign.plusAssign(classX: ClassWithExtensionPlusAssign) {
    field += classX.field
}

class ClassWithMemberPlus(private var field: Int) {
    private operator fun plus(other: ClassWithMemberPlus): ClassWithMemberPlus {
        return ClassWithMemberPlus(field + other.field)
    }
}

class ClassWithExtensionPlus(var field: Int)

private operator fun ClassWithExtensionPlus.plus(other: ClassWithExtensionPlus): ClassWithExtensionPlus {
    return ClassWithExtensionPlus(field + other.field)
}

class X {
    private var intField = 0
    private var fieldWithMemberInc: ClassWithMemberInc = ClassWithMemberInc(2)
    private var fieldWithExtensionInc: ClassWithExtensionInc = ClassWithExtensionInc(4)
    private var fieldWithMemberPlusAssign: ClassWithMemberPlusAssign = ClassWithMemberPlusAssign(5)
    private var fieldWithExtensionPlusAssign: ClassWithExtensionPlusAssign = ClassWithExtensionPlusAssign(6)
    private var fieldWithMemberPlus = ClassWithMemberPlus(7)
    private var fieldWithExtensionPlus = ClassWithExtensionPlus(8)
}

fun main() {
    val x = X()

    //Breakpoint!
    val y = 1
}

// EXPRESSION: x.intField++
// RESULT: 0: I

// EXPRESSION: x.intField++
// RESULT: 1: I

// EXPRESSION: (x.fieldWithMemberInc++).field
// RESULT: 2: I

// EXPRESSION: (x.fieldWithMemberInc++).field
// RESULT: 3: I

// EXPRESSION: (x.fieldWithExtensionInc++).field
// RESULT: 4: I

// EXPRESSION: (x.fieldWithExtensionInc++).field
// RESULT: 5: I

// EXPRESSION: x.fieldWithMemberPlusAssign += ClassWithMemberPlusAssign(1); x.fieldWithMemberPlusAssign.field
// RESULT: 6: I

// EXPRESSION: x.fieldWithExtensionPlusAssign += ClassWithExtensionPlusAssign(1); x.fieldWithExtensionPlusAssign.field
// RESULT: 7: I

// EXPRESSION: x.fieldWithMemberPlus += ClassWithMemberPlus(1); x.fieldWithMemberPlus.field
// RESULT: 8: I

// EXPRESSION: x.fieldWithExtensionPlus += ClassWithExtensionPlus(1); x.fieldWithExtensionPlus.field
// RESULT: 9: I

// See KTIJ-32493
// IGNORE_K1