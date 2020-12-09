import java.util.Objects;

public class EqualsAndHashCodeWithNamedExistingMethods {
    private int someInt;
    private Integer someInteger;

    public boolean equals(Object o, Object o2) {
        return o.equals(o2);
    }

    public int hashCode(Float someFloat) {
        return Objects.hash(someFloat);
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof EqualsAndHashCodeWithNamedExistingMethods)) return false;
        final EqualsAndHashCodeWithNamedExistingMethods other = (EqualsAndHashCodeWithNamedExistingMethods) o;
        if (!other.canEqual((Object) this)) return false;
        if (this.someInt != other.someInt) return false;
        final Object this$someInteger = this.someInteger;
        final Object other$someInteger = other.someInteger;
        if (this$someInteger == null ? other$someInteger != null : !this$someInteger.equals(other$someInteger))
            return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof EqualsAndHashCodeWithNamedExistingMethods;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.someInt;
        final Object $someInteger = this.someInteger;
        result = result * PRIME + ($someInteger == null ? 43 : $someInteger.hashCode());
        return result;
    }
}
