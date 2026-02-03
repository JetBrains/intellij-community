// IGNORE_K2
public class A implements I {
    private final String s;

    public A(String s) {
        this.s = s;
    }

    @Override
    public String getS() {
        return s;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof A)) return false;

        A a = (A) o;

        return s != null ? s.equals(a.s) : a.s == null;
    }

    @Override
    public int hashCode() {
        return s != null ? s.hashCode() : 0;
    }
}