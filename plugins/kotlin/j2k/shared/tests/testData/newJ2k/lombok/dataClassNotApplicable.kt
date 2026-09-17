// !ADD_LOMBOK_ANNOTATIONS
// KTIJ-12602: Kotlin refuses `data` on these classes, so only the constructor may change
package test

import lombok.Data
import lombok.EqualsAndHashCode
import lombok.ToString

@EqualsAndHashCode
@ToString
internal abstract class AbstractPojo(val abstractField: Int)

@EqualsAndHashCode
@ToString
internal class NoRequiredFieldPojo {
    var justMutable: Int = 0
}

@Data(staticConstructor = "of")
internal class StaticConstructorPojo {
    private val withStaticConstructor = 0
}

@Data
internal class PojoWithOwnConstructor(private val own: Int)

@Data
internal enum class DataEnum {
    A, B
}

internal class Outer {
    @EqualsAndHashCode
    @ToString
    internal inner class InnerPojo(val innerField: Int)
}
