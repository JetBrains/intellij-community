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
        private List<? super Container> containerChildren = new ArrayList<>();

        public Container(Element... children) {
            this.children = new ArrayList<>(Arrays.asList(children));
        }

        public List<? extends Element> getChildren() {
            return children;
        }

        private void testAssignment1(List<? super Element> elements) {
            elements = getChildren();
        }

        private void testAssignment2(List<? super Element> elements) {
            elements = containerChildren;
        }

        public List<? super Container> mergeWithChildren(List<? super Element> other) {
            other.addAll(children);
            return other;
        }

        public Box<? extends Container> getBox() {
            Box<Container> box = new Box(new Container());
            return box;
        }
    }
}