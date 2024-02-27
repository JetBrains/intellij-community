import java.util.Locale

internal class A {
    fun toUpperCase() {
        val s = "kotlin"
        s.uppercase(Locale.getDefault())
        s.uppercase(Locale.getDefault())
        s.uppercase()
        s.uppercase(Locale.FRENCH)
        s.uppercase()
        s.uppercase()
    }

    fun toLowerCase() {
        val s = "kotlin"
        s.lowercase(Locale.getDefault())
        s.lowercase(Locale.getDefault())
        s.lowercase()
        s.lowercase(Locale.FRENCH)
        s.lowercase()
        s.lowercase()
    }
}
