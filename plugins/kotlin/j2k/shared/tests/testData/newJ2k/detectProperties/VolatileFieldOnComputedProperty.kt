import kotlin.concurrent.Volatile

// A volatile field is var, so the property keeps its backing field when the getter does not read it
class Holder {
    private val name: String? = null

    @Volatile
    var label: String? = null
        get() = "Label: " + name
        private set
}
