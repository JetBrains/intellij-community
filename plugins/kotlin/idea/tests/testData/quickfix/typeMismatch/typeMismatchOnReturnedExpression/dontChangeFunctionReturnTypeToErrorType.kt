// "Change 'foo' function return type to '(x: [ERROR : NoSuchType]) -> Int'" "false"
// ACTION: Convert to multi-line lambda
// ACTION: Create annotation 'NoSuchType'
// ACTION: Create class 'NoSuchType'
// ACTION: Create enum 'NoSuchType'
// ACTION: Create interface 'NoSuchType'
// ACTION: Create type parameter 'NoSuchType' in function 'foo'
// ACTION: Enable a trailing comma by default in the formatter
// ACTION: Remove explicit lambda parameter types (may break code)
// ERROR: Type mismatch: inferred type is ([Error type: Unresolved type for NoSuchType]) -> Int but Int was expected
// ERROR: Unresolved reference: NoSuchType
// K2_AFTER_ERROR: Return type mismatch: expected 'Int', actual 'Function1<ERROR CLASS: Symbol not found for NoSuchType, Int>'.
// K2_AFTER_ERROR: Unresolved reference 'NoSuchType'.

fun foo(): Int {
    return { x: NoSuchType<caret> -> 42 }
}