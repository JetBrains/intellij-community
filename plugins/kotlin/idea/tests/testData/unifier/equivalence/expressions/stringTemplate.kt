fun foo(n: Int, f: (Int) -> Int) {
    <selection>"test: $n, ${f(n)} \n"</selection>
    "test: ${n}, ${f(n)} \n"
    ("test: ${n}, ${f(n)} \n")
    "test: ${f(n)}, ${n} \n"
    "test: n, ${f(n)} \n"
    "test: $n, $f \n"
}