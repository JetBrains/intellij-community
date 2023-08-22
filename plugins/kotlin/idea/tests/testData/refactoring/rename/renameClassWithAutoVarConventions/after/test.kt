package test

class Bar {
  companion object {
      val barKlass: Class<Bar> = Bar::class.java
  }
}

val SOME_Bar: Bar = Bar()
val Bar: Bar = Bar()

val some_bar: Bar = Bar()
val bar: Bar = Bar()

val SomeBar: Bar = Bar()
val bar2 = Bar()