class A {
    {
        Object o;

        try {
            o = newMethod();
        }
        catch (Exception e) {
        }

        o.f();
    }

    private Object newMethod() {
        Object o;
        o = foo();
        return o;
    }
}