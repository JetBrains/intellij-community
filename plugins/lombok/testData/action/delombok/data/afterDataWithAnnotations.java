import lombok.NonNull;

public class DataWithAnnotations {

    @NonNull
    @Deprecated
    @SuppressWarnings("any")
    private Integer someParentInteger;

    public DataWithAnnotations(@NonNull Integer someParentInteger) {
        this.someParentInteger = someParentInteger;
    }

    @Deprecated
    public @NonNull Integer getSomeParentInteger() {
        return this.someParentInteger;
    }

    @Deprecated
    public void setSomeParentInteger(@NonNull Integer someParentInteger) {
        this.someParentInteger = someParentInteger;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof DataWithAnnotations)) return false;
        final DataWithAnnotations other = (DataWithAnnotations) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$someParentInteger = this.getSomeParentInteger();
        final Object other$someParentInteger = other.getSomeParentInteger();
        if (this$someParentInteger == null ? other$someParentInteger != null : !this$someParentInteger.equals(other$someParentInteger))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DataWithAnnotations;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $someParentInteger = this.getSomeParentInteger();
        result = result * PRIME + ($someParentInteger == null ? 43 : $someParentInteger.hashCode());
        return result;
    }

    public String toString() {
        return "DataWithAnnotations(someParentInteger=" + this.getSomeParentInteger() + ")";
    }
}
