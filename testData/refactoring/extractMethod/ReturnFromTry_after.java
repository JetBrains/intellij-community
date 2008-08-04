class A {
    public String method() {
        try {
            return newMethod();
        }
        catch (Error e) {

        }
        return "";
    }

    private String newMethod() {
        try {
            return "";
        }
        finally {
            System.out.println("f");
        }
    }
}