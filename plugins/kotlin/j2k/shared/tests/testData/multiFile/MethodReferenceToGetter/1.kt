// KTIJ-39463: a Java method reference cannot become a field access, so the getter must survive
package test

class Location(val unLocode: String?, @JvmField val name: String?)
