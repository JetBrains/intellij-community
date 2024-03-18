public class TestFieldInitializer {
    private String string = "";

    public TestFieldInitializer(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }
}