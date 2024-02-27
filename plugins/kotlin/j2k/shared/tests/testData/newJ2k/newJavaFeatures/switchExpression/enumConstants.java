enum ColorEnum {
    GREEN
}

class MyClass {
    int method(ColorEnum colorEnum) {
        return switch (colorEnum) {
            case GREEN:
                yield 1;
            default:
                yield 2;
        };
    }
}