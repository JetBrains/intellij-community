// IGNORE_K1
interface A
interface B

class ClassPropertyAmbiguous {
    val b: A = <selection>object : B, A {}</selection>
}