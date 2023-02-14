class A {
    // ascii escapes
    var ascii1 = "\t\b\n\r\'\"\\"
    var ascii2 = "\u000c"
    var ascii3 = "\\f"
    var ascii4 = "\\\u000c"

    // backslash
    var backslash1 = "\u0001"
    var backslash2 = "\\1"
    var backslash3 = "\\\u0001"
    var backslash4 = "\\\\1"
    var backslash5 = "\\\\\u0001"
    var backslash6 = "\u0001\u0001"

    // dollar
    var dollar1 = "\$a"
    var dollar2 = "\$A"
    var dollar3 = "\${s}"
    var dollar4 = "$$"

    // octal
    var octal1 = "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004"
    var octal2 =
        "\u0001\u0000\u0001\u0001\u0001\u0002\u0001\u0003\u0001\u0004" + "\u0001\u000d\u0001\u000c\u0001\u000d\u0002\u0001\u0001"
    var octal3 = "\u003f0123"
    var octal4 = "\u00490123"
    var octal5 = "\u00ff0123"
    var octal6 = "\u0000Text"
    var octal7 = "\u00009"
}
