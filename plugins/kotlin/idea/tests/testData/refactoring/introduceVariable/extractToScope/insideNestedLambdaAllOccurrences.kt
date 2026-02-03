fun main() {

    var b = 42

    run {
        run {
            run {
                <selection>b</selection>
                b
            }
            b
        }
        b
    }

    b
}