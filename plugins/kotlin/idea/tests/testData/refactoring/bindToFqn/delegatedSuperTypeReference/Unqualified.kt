// BIND_TO test.B
package test

interface A

interface B

interface Base : A, B

class Delegation(base: Base) : test.<caret>A by base