import lombok.ToString;

@ToString
public class ToStringWithNamedExistingMethods {
<caret>
    private int someInt;

    public String toString(String string) {
        return string;
    }
}
