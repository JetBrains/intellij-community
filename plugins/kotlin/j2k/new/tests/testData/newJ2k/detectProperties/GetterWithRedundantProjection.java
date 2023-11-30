import java.util.*;

public class J {
    public interface Element {}

    public static class Container implements Element {
        private final List<Element> children;

        public Container(Element... children) {
            this.children = new ArrayList<>(Arrays.asList(children));
        }

        public List<? extends Element> getChildren() {
            return children;
        }
    }
}