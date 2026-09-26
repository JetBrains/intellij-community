package test

@JvmInline
value class ValueClass(val value: String) {}

@JvmExposeBoxed("kotlinExtension")
fun ValueClass.extensionExposed(): ValueClass = ValueClass(this.value)