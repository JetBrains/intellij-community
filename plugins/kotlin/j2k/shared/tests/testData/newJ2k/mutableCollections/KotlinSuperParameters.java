import java.util.*;

class Derived extends Base<String> {
    @Override
    public void mutable(List<String> items) {
    }

    @Override
    public void aliased(List<String> names) {
    }

    @Override
    public void nested(Map<String, ? extends List<Integer>> map) {
    }

    @Override
    public void generic(Collection<? extends String> items) {
    }
}
