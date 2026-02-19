// "Change return type of called function 'User.User' to 'String'" "false"
// ERROR: Type mismatch: inferred type is User but String was expected
// K2_AFTER_ERROR: Return type mismatch: expected 'String', actual 'User'.
class User(val id: Int)

fun getUserId(): String {
    return User<caret>(123)
}