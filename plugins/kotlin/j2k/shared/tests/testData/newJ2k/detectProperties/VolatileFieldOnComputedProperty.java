// A volatile field is var, so the property keeps its backing field when the getter does not read it
public class Holder {
    private String name;
    private volatile String label;

    public String getLabel() {
        return "Label: " + name;
    }
}
