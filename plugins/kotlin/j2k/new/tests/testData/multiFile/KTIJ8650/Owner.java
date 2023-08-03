public class Owner {
    private String string;

    public synchronized String getString() {
        if (string == null) {
            string = "";
        }
        return string;
    }

    public synchronized String getString(String defaultValue) {
        if (string == null) {
            string = defaultValue;
        }
        return string;
    }
}