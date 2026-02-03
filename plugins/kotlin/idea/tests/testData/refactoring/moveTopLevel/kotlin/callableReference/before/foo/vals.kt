package foo

import bar.CrExtended

val Int.extValWithFunType get() = fun (): Unit {}
val Int.extValWithExtFunType get() = fun CrExtended.(): Unit {}

