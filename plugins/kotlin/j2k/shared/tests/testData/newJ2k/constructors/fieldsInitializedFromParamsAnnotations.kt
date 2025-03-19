// ERROR: This annotation is not applicable to target 'value parameter' and use-site target '@param'. Applicable targets: class, function, property, annotation class, constructor, setter, getter, typealias
// ERROR: This annotation is not applicable to target 'value parameter' and use-site target '@param'. Applicable targets: class, function, property, annotation class, constructor, setter, getter, typealias
// ERROR: This annotation is not applicable to target 'backing field' and use-site target '@field'. Applicable targets: class, function, property, annotation class, constructor, setter, getter, typealias
internal class C(
    @field:Deprecated("") private val p1: Int, @param:Deprecated("") private val myP2: Int, @param:Deprecated(
        ""
    ) var p3: Int
)
