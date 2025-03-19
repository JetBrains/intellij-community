class ArrayField {
    void test() {
        String[] array = new String[0];

        for (String s : array) {
            System.out.println(s.length());
        }
    }
}