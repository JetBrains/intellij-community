// ERROR: 'x' hides member of supertype 'I' and needs an 'override' modifier.
package test

class J(@JvmField val x: Int) : I
