class Foo1 { /// M
    val x: String /// F, L
        get() = "foo" /// M
}

class Foo2 { /// M
    val x: String = "foo" /// F, L
        get() = field + "x" /// M
}

class Foo3 { /// M
    val x: String = "foo" /// F, L
        get() { /// M
            return run { field + "x" } /// *, L, λ
        }
}

class Foo4 { /// M
    val x: String = "foo" /// F, L
        get() = run { field + "x" } /// *, L, M, λ
}
