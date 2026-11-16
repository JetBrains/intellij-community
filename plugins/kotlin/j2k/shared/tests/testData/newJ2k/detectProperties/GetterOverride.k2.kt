// ERROR: ABSTRACT_MEMBER_NOT_IMPLEMENTED
// ERROR: NOTHING_TO_OVERRIDE
//Interface
interface Parent {
    val x: Unit
}

//Subclass
class Child : Parent {
    override fun getX() {
    }
}
