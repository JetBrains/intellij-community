class A(param: String)
typealias AA = A
typealias AAA = AA

val a = AA("10")
val b = A("10")
val c = AAA("10")