open class A constructor(
    val key: String,
    val alternateKeys: List<String> = listOf(""),
    val epoch: Int = 0,
    val required: Boolean = true,
    val label: String,
    val description: String? = null,
    val defaultValue: Any? = null,
    val type: Type = BType,
    open val children: List<A> = listOf(),
    open val iterable: Iterable<String>? = null
)
open class Type(val configFileName: String?)
object BType : Type("")
open class AImpl(
    key: String,
    label: String,
    type: Type = BType,
    children: List<A> = listOf()
) : A(key = key, label = label, type = type, children = children) {
    val partialProfileKey = key
}
