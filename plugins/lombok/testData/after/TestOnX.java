package action.delombok.onx;

import javax.inject.Inject;
import javax.inject.Named;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import lombok.NonNull;

public class TestOnX {
    @NonNull
    private final Integer someIntField;
    /** @deprecated */
    @Deprecated
    @NonNull
    private String someStringField;
    private float someFloatField;

    public static void main(String[] args) {
        TestOnX test1 = new TestOnX(1, "str");
        System.out.println(test1);
        TestOnX test2 = new TestOnX(2, "str", 3.0F);
        System.out.println(test2);
    }

    public String toString() {
        return "TestOnX(someIntField=" + this.getSomeIntField() + ", someStringField=" + this.getSomeStringField() + ", someFloatField=" + this.someFloatField + ")";
    }

    @Inject
    @Named("myName1")
    public TestOnX(@NonNull Integer someIntField, @NonNull String someStringField) {
        if (someIntField == null) {
            throw new NullPointerException("someIntField is marked non-null but is null");
        } else if (someStringField == null) {
            throw new NullPointerException("someStringField is marked non-null but is null");
        } else {
            this.someIntField = someIntField;
            this.someStringField = someStringField;
        }
    }

    @Inject
    @Named("myName2")
    public TestOnX(@NonNull Integer someIntField, @NonNull String someStringField, float someFloatField) {
        if (someIntField == null) {
            throw new NullPointerException("someIntField is marked non-null but is null");
        } else if (someStringField == null) {
            throw new NullPointerException("someStringField is marked non-null but is null");
        } else {
            this.someIntField = someIntField;
            this.someStringField = someStringField;
            this.someFloatField = someFloatField;
        }
    }

    public boolean equals(@Valid Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof TestOnX)) {
            return false;
        } else {
            TestOnX other = (TestOnX)o;
            if (!other.canEqual(this)) {
                return false;
            } else if (Float.compare(this.someFloatField, other.someFloatField) != 0) {
                return false;
            } else {
                Object this$someIntField = this.getSomeIntField();
                Object other$someIntField = other.getSomeIntField();
                if (this$someIntField == null) {
                    if (other$someIntField != null) {
                        return false;
                    }
                } else if (!this$someIntField.equals(other$someIntField)) {
                    return false;
                }

                Object this$someStringField = this.getSomeStringField();
                Object other$someStringField = other.getSomeStringField();
                if (this$someStringField == null) {
                    if (other$someStringField != null) {
                        return false;
                    }
                } else if (!this$someStringField.equals(other$someStringField)) {
                    return false;
                }

                return true;
            }
        }
    }

    protected boolean canEqual(@Valid Object other) {
        return other instanceof TestOnX;
    }

    public int hashCode() {
        int PRIME = true;
        int result = 1;
        int result = result * 59 + Float.floatToIntBits(this.someFloatField);
        Object $someIntField = this.getSomeIntField();
        result = result * 59 + ($someIntField == null ? 43 : $someIntField.hashCode());
        Object $someStringField = this.getSomeStringField();
        result = result * 59 + ($someStringField == null ? 43 : $someStringField.hashCode());
        return result;
    }

    @Max(100)
    @NonNull
    public Integer getSomeIntField() {
        return this.someIntField;
    }

    /** @deprecated */
    @Deprecated
    @Size(
        max = 20
    )
    @NonNull
    public String getSomeStringField() {
        return this.someStringField;
    }

    /** @deprecated */
    @Deprecated
    @Size(
        min = 10
    )
    public void setSomeStringField(@Size(min = 15) @NonNull String someStringField) {
        if (someStringField == null) {
            throw new NullPointerException("someStringField is marked non-null but is null");
        } else {
            this.someStringField = someStringField;
        }
    }

    @NonNull
    public TestOnX withSomeFloatField(@Min(1) float someFloatField) {
        return this.someFloatField == someFloatField ? this : new TestOnX(this.someIntField, this.someStringField, someFloatField);
    }
}
