fun test(s1: String, s2: String, i: Int, s3: String, s4: String) = Unit

fun usages2() {
    test("s1", "s2", 42, "s3", "s4")
    test("s1", "s2", 42, "s3", s4 = "s4")
    test("s1", "s2", 42, s3 = "s3", s4 = "s4")
    test("s1", "s2", 42, s3 = "s3", s4 = "s4")
    test("s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test("s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test("s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test("s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
    test(s1 = "s1", s2 = "s2", i = 42, s3 = "s3", s4 = "s4")
}
