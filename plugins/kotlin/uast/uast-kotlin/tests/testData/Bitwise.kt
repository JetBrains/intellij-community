fun foo(): Int {
    val mask: Int = 0x7f
    val x: Int = 0b010_1010_1010_1010_1010_1010_1010_1010

    val pos = x and mask
    val max = x or mask
    val zebra = x xor mask

    val signed = x shr 2
    val one = x ushr 29
    val zero = x shl 31

    return pos + zero - zebra * signed / one
}

fun bar(): Long {
    val mask: Long = 0x7f
    val x: Long = 0x2AAAAAAAAAAAAAAA

    val pos = x and mask
    val max = x or mask
    val zebra = x xor mask

    val signed = x shr 2
    val one = x ushr 61
    val zero = x shl 63

    return pos + zero - zebra * signed / one
}

