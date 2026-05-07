// RENAME: variable
// NEW_NAME: A
package foo

import foo.SimpleClass as Simple

class SimpleClass

fun x(): Simple = Simple<caret>()
