// NEW_NAME: name1
// RENAME: member
// IGNORE_K2

package test

class ShadeKotlin {
    val name1 = 1;
    fun inner() {
        val <caret>name2 = 2;
        print(name1)
        print(name2)
    }
}