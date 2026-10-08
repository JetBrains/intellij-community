// ERROR: Class base.Base uses function 'fun hidden()', which is not accessible from the class child.Child
package base

private fun hidden() {}

open class <caret>Base {
    init { hidden() }
}
