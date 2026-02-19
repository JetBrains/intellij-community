// RENAME: member
interface SelfReferencing<T : SelfReferencing<T>> {
    fun reference(): T
}

class ComplexSelfReferencing : SelfReferencing<ComplexSelfReferencing> {
    override fun reference(): ComplexSelfReferencing = <caret>this@ComplexSelfReferencing
}