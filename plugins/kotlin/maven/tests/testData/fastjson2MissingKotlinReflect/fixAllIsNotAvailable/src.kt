import com.alibaba.fastjson2.JSON

data class User(val name: String, val age: Int)

fun main() {
    JSON.parse<caret>Object("""{"name":"Alice","age":30}""", User::class.java)
}
