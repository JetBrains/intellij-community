final class Class1 {


    private final String f1;

    @lombok.EqualsAndHashCode.Exclude
    private final String f2;

    public Class1(String f1, String f2) {
        this.f1 = f1;
        this.f2 = f2;
    }

    public String getF1() {
        return this.f1;
    }

    public String getF2() {
        return this.f2;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof Class1)) return false;
        final Class1 other = (Class1) o;
        final Object this$f1 = this.getF1();
        final Object other$f1 = other.getF1();
        if (this$f1 == null ? other$f1 != null : !this$f1.equals(other$f1)) return false;
        return true;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $f1 = this.getF1();
        result = result * PRIME + ($f1 == null ? 43 : $f1.hashCode());
        return result;
    }

    public String toString() {
        return "Class1(f1=" + this.getF1() + ", f2=" + this.getF2() + ")";
    }
}