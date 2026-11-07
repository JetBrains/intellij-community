// ERROR: 'isVisible' hides member of supertype 'KotlinClassAbstractProperty' and needs an 'override' modifier.
// !ADD_KOTLIN_API
package test

import kotlinApi.KotlinClassAbstractProperty

class KotlinClassAbstractPropertyImpl : KotlinClassAbstractProperty() {
    var isVisible: Boolean = false
        private set

    private fun test() {
        this.isVisible = true
    }
}
