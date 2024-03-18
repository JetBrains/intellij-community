// "Cast expression '1' to 'UInt'" "false"
// WITH_STDLIB
// ERROR: Conversion of signed constants to unsigned ones is prohibited
// ACTION: Add 'u =' to argument
// ACTION: Change parameter 'u' type of function 'takeUInt' to 'Int'
// ACTION: Change to '1u'
// ACTION: Convert property initializer to getter
// ACTION: Convert to lazy property
// ACTION: Do not show hints for current method
// ACTION: Enable option 'Property types' for 'Types' inlay hints

fun takeUInt(u: UInt) = 0

val b = takeUInt(<caret>1)