internal class AccessorsArePreserved {
    var someInt: Int = 1
        // get header
        get() {
            // get body 1
            println("Some text")
            // get body 2
            return field
        } // get footer
        // set header
        set(state) {
            // set body 1
            field = state
            // set body 2
            println("Some text")
        } // set footer
}

internal class AccessorsAreRemoved {
    // set body
// set footer
// set header
// get body
// get footer
// get header
    var someInt: Int = 1
}
