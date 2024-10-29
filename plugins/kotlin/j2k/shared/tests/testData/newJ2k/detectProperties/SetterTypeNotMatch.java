class A {
    private Object o;

    public Object getValue() {
        return o;
    }

    public void setValue(String s) {
        takesString(s);
        o = s;
    }

    private void takesString(String s) {}
}