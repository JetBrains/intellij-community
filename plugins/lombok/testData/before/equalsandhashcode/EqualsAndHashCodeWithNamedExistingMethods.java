import lombok.EqualsAndHashCode;

import java.util.Objects;

@EqualsAndHashCode
public class EqualsAndHashCodeWithNamedExistingMethods {
    <caret>
    private int someInt;
    private Integer someInteger;

    public boolean equals(Object o, Object o2) {
        return o.equals(o2);
    }

    public int hashCode(Float someFloat) {
        return Objects.hash(someFloat);
    }
}
