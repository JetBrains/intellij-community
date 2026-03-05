package pb

import pa.Bar

class <caret>WithList {
    var list: List<Bar> = emptyList()

    fun foo() {
        list += Bar()
    }
}
