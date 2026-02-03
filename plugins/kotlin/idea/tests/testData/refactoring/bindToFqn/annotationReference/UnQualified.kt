// BIND_TO test.B
package test

annotation class A { }

annotation class B { }

@<caret>A
class C {}