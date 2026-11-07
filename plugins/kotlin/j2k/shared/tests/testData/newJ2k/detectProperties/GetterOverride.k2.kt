// ERROR: 'getX' overrides nothing.
// ERROR: Class 'Child' is not abstract and does not implement abstract member: val x: Unit
//Interface
interface Parent {
    val x: Unit
}

//Subclass
class Child : Parent {
    override fun getX() {
    }
}
