// "Replace with safe (?.) call" "true"
// WITH_STDLIB

fun foo
{
    val nullable: Int? = null
    "" to nullable<caret>.div(10)

}