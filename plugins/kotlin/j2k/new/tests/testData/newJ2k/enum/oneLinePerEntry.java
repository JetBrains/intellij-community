enum BigEnum {
    ENUM_ONE("SOME_NAME_TO_PUT_HERE"),
    ENUM_TWO("SOME_NAME_TO_PUT_HERE"),
    ENUM_THREE("SOME_NAME_TO_PUT_HERE"),
    ENUM_FOUR("SOME_NAME_TO_PUT_HERE"),
    ENUM_FIVE("SOME_NAME_TO_PUT_HERE"),
    ENUM_SIX("SOME_NAME_TO_PUT_HERE"),
    ENUM_SEVEN("SOME_NAME_TO_PUT_HERE"),
    ENUM_EIGHT("SOME_NAME_TO_PUT_HERE"),
    ENUM_NINE_HUNDRED_NINETY_NINE("SOME_NAME_TO_PUT_HERE"),
    ENUM_ZEO("SOME_NAME_TO_PUT_HERE");

    BigEnum(String name) {

    }
}

enum Formatting {
    A,

    B,


    C,
    D
    ,
    E

    ,

    F {void foo() {}}
    ,

    G {
        void foo() {}
    }
    ,

    H
            {
                void foo() {}
            }
}

enum Formatting2 {

    A("hello"
    ),

    B(
            "hello"
    )
    ,

    C(
            "hello"),
    D("hello"), E("hello"),

    F(
            "hello"
    ) {
        void foo() {}
    }, G("hello") {void foo() {}}, H("hello") {
        void foo() {}
    };

    Formatting2(String s) {
    }
}