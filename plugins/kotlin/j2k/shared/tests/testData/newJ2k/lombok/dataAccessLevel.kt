// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: @Getter and @Setter on a field change the visibility that @Data would generate
package test

import lombok.EqualsAndHashCode
import lombok.ToString

data class Levels(val finalField: String) {
    var defaultLevels: String? = null

    protected var protectedGetter: String? = null

    var packageGetter: String? = null

    private var privateGetter: String? = null

    private var noGetter: String? = null

    val noSetter: String? = null

    private val noAccessors: String? = null

    var explicitDefaultGetter: String? = null
}

@EqualsAndHashCode
@ToString
internal class ClassLevelGetter {
    protected var fromClassLevel: String? = null

    var fieldOverridesClass: String? = null
}
