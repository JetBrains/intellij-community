class A {
    // ascii escapes
    var ascii1: String = "\t\b\n\r\'\"\\"
    var ascii2: String = "\u000c"
    var ascii3: String = "\\f"
    var ascii4: String = "\\\u000c"

    // backslash
    var backslash1: String = "\u0001"
    var backslash2: String = "\\1"
    var backslash3: String = "\\\u0001"
    var backslash4: String = "\\\\1"
    var backslash5: String = "\\\\\u0001"
    var backslash6: String = "\u0001\u0001"

    // dollar
    var dollar1: String = "\$a"
    var dollar2: String = "\$A"
    var dollar3: String = "\${s}"
    var dollar4: String = "$$"

    // octal
    var octal1: String = "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004"
    var octal2: String =
        "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004" + "\u0001\u000d\u0001\u000c\u0001\u000d\u0002\u0001\u0001"
    var octal3: String = "\u003f0123"
    var octal4: String = "\u00490123"
    var octal5: String = "\u00ff0123"
    var octal6: String = "\u0000Text"
    var octal7: String = "\u00009"
}
