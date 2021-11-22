//method
public void foo(int i, int j) {
    String a = switch (i) {
        case 0:
            switch (j) {
                case 1:
                    yield "0, 1";
                default:
                    yield "0, x";
            }
        case 1:
            yield "1, x";
        default:
            yield "x, x";
    }
}