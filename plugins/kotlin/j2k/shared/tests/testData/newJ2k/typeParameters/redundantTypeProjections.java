// IGNORE_K2
import java.util.*;

public class J {
    public interface Element {}

    public class Box<T> {
        private T t;

        public Box(T t) {
            this.t = t;
        }

        public void putT(T t) {
            this.t = t;
        }

        public T getT() {
            return t;
        }
    }

    public class Container implements Element {
        private final List<Element> children;
        private List<? extends Container> containerChildren = new ArrayList<>();

        public Container(Element... children) {
            this.children = new ArrayList<>(Arrays.asList(children));
        }

        public List<? extends Element> getChildren() {
            return children;
        }

        private void testAssignment1(List<Element> elements) {
            elements = getChildren();
        }

        private void mergeIntoParameter(List<? super Element> elements) {
            elements.addAll(containerChildren);
        }

        public Box<? extends Container> getBox() {
            Box<Container> box = new Box<>(new Container());
            return box;
        }
    }
}