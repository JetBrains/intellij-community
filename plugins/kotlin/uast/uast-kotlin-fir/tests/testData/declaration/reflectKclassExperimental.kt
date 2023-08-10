package androidx.annotation.experimental

import kotlin.annotation.Retention
import kotlin.annotation.Target
import kotlin.reflect.KClass

@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.PROPERTY,
    AnnotationTarget.LOCAL_VARIABLE,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.FILE,
    AnnotationTarget.TYPEALIAS
)
annotation class UseExperimental(
    /**
     * Defines the experimental API(s) whose usage this annotation allows.
     */
    vararg val markerClass: KClass<out Annotation>
)