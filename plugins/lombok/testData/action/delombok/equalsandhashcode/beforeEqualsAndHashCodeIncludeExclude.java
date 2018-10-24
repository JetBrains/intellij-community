@lombok.EqualsAndHashCode
public class EqualsAndHashCodeIncludeExclude {
    int x;
    @lombok.EqualsAndHashCode.Exclude
    boolean bool;
    @lombok.EqualsAndHashCode.Include(replaces = "aaa")
    String a;

    int getX() {
        return x;
    }

    boolean isBool() {
        return bool;
    }

    String getA() {
        return a;
    }
}
