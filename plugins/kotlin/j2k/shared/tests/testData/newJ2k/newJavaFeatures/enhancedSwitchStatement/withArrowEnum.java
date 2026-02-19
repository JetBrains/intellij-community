enum ColorEnum {
    GREEN, RED, YELLOW
}

class MyClass {
    void method(ColorEnum colorEnum) {
        switch (colorEnum) {
            case GREEN, YELLOW -> System.out.println(1);
            default -> System.out.println(2);
        }
    }
}