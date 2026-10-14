interface Marker

open class <caret>Base : Marker {
    fun answer() = 42
}

class Child : Base(), Marker
