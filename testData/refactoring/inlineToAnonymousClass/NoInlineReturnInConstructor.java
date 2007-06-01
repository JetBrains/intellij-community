class A {
    private Object b = new MyException();

    private class <caret>MyException extends Exception {
        public MyException() {
            super("w");
            if (toString().length() == 0) return;
            System.out.println(toString());
        }

        public String getMessage() {
            return "q";
        }
    }
}