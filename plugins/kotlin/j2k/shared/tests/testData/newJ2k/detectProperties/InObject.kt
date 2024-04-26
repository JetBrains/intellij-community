object AAA {
    var x: Int = 42
    var y: Int = 0
    var z: Int = 0
        set(z) {
            Other.z = z
        }
}

internal object Other {
    var z: Int = 0
}
