// NAME: UserBase
package example

@JvmInline
value class Id(val value: String)

data class ShoeSize(val value: Int)

// SIBLING:
class <caret>User(
    // INFO: {"checked": "true", "toAbstract": "true"}
    val id: Id,
    // INFO: {"checked": "true", "toAbstract": "true"}
    val name: String,
    // INFO: {"checked": "true", "toAbstract": "true"}
    val shoeSize: ShoeSize
)
