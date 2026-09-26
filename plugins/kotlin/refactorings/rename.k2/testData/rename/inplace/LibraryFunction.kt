// NEW_NAME: map1
// SHOULD_FAIL_WITH: Cannot perform refactoring. This element cannot be renamed
// RENAME: member
fun foo(list: List<Int>): List<String> = list.map<caret>{ it.toString() }