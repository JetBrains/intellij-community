package test

@JvmInline
value class ValueClass(val value: String) {}

@JvmExposeBoxed("bar")
fun ValueClass.bar(): ValueClass = ValueClass(this.value)