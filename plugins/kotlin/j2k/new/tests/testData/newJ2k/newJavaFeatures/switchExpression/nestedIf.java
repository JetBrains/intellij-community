//method
public void foo(int i, int j) {
    String a = switch (i) {
        case 0:
            if (j > 0) {
                yield "1"
            } else {
                yield "2"
            }
        case 1:
            yield "3";
        default:
            yield "4";
    }
}