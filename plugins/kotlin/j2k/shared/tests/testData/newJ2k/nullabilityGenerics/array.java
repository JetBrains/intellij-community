// TODO support array initializers

class ArrayArgument {
    void test(String[] array) {
        for (String s : array) {
            System.out.println(s.hashCode());
        }

        takesArray(array);
    }

    private void takesArray(String[] array) {
    }
}

class ArrayMethodCall {
    void test() {
        String[] array = strings();

        for (String s : array) {
            System.out.println(s.hashCode());
        }
    }

    private String[] strings() {
        return strings2();
    }

    private String[] strings2() {
        return new String[0];
    }
}

class ArrayParameter {
    void test(String[] param) {
        String[] array = param;

        for (String s : array) {
            System.out.println(s.hashCode());
        }
    }
}

class ArrayField {
    String[] field = new String[0];

    void test() {
        String[] array = field;

        for (String s : array) {
            System.out.println(s.hashCode());
        }
    }
}