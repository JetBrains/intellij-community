// BIND_TO test.B
package test

annotation class A { }

annotation class B { }

@test.<caret>A
class C {}