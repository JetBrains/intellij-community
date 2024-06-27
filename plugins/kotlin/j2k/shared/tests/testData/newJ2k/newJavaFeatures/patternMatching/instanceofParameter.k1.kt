import java.util.Locale

class CheckPm {
    fun f(obj: Any) {
        if (obj is String) {
            println(obj.lowercase(Locale.getDefault()))
        }
    }
}
