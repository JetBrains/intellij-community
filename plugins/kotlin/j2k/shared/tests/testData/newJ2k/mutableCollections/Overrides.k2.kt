// ERROR: 'foo' overrides nothing. Potential signatures for overriding: fun foo(mutableCollection: MutableCollection<String>, nullableCollection: Collection<Int>?): MutableList<Any>
// ERROR: Argument type mismatch: actual type is 'MutableCollection<Int?>?', but 'Collection<Int>?' was expected.
// ERROR: Argument type mismatch: actual type is 'MutableCollection<String?>', but 'MutableCollection<String>' was expected.
// !ADD_KOTLIN_API
package javaApi

import kotlinApi.KotlinClass

abstract class C(field: Int) : KotlinClass(field) {
    override fun foo(
        mutableCollection: MutableCollection<String?>,
        nullableCollection: MutableCollection<Int?>?
    ): MutableList<Any> {
        return super.foo(mutableCollection, nullableCollection)
    }
}
