public class ToStringWithNamedExistingMethods {
    private int someInt;

    public String toString(String string) {
        return string;
    }

    public String toString() {
        return "ToStringWithNamedExistingMethods(someInt=" + this.someInt + ")";
    }
}
