class MyClass {
    void method(String str) {
        switch (str) {
            case "1", "2"  -> System.out.println(12);
            case "3"  -> System.out.println(3);
            default -> System.out.println(4);
        }
    }
}