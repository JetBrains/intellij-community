class C {
    boolean test1(String key) {
        return key.equals("one") // comment 1
               || key.equals("two"); // comment 2
    }

    boolean test2(String key) {
        return key.equals("one") || // comment 1
               key.equals("two"); // comment 2
    }

    boolean test3(String key) {
        return key.equals("one") || key.equals("two"); // comment
    }
}