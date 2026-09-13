// KTIJ-39463: a Java method reference cannot become a field access, so the getter must survive
package test;

public class Location {
    private final String unLocode;
    private final String name;

    public Location(String unLocode, String name) {
        this.unLocode = unLocode;
        this.name = name;
    }

    public String getUnLocode() {
        return unLocode;
    }

    public String getName() {
        return name;
    }
}
