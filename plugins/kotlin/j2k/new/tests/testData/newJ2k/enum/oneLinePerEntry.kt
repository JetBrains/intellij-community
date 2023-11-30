internal enum class BigEnum(name: String) {
    ENUM_ONE("SOME_NAME_TO_PUT_HERE"),
    ENUM_TWO("SOME_NAME_TO_PUT_HERE"),
    ENUM_THREE("SOME_NAME_TO_PUT_HERE"),
    ENUM_FOUR("SOME_NAME_TO_PUT_HERE"),
    ENUM_FIVE("SOME_NAME_TO_PUT_HERE"),
    ENUM_SIX("SOME_NAME_TO_PUT_HERE"),
    ENUM_SEVEN("SOME_NAME_TO_PUT_HERE"),
    ENUM_EIGHT("SOME_NAME_TO_PUT_HERE"),
    ENUM_NINE_HUNDRED_NINETY_NINE("SOME_NAME_TO_PUT_HERE"),
    ENUM_ZEO("SOME_NAME_TO_PUT_HERE")
}

internal enum class Formatting {
    A,

    B,


    C,
    D,
    E,

    F {
        fun foo() {}
    },

    G {
        fun foo() {}
    },

    H {
        fun foo() {}
    }
}

internal enum class Formatting2(s: String) {
    A(
        "hello"
    ),

    B(
        "hello"
    ),

    C(
        "hello"
    ),
    D("hello"), E("hello"),

    F(
        "hello"
    ) {
        fun foo() {}
    },
    G("hello") {
        fun foo() {}
    },
    H("hello") {
        fun foo() {}
    }
}
