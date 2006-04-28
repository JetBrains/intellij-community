import java.util.*;

class Foo {
    public void foo(Bar bar) {
        for (Iterator it = bar.iterator(); it.hasNext();) {
            final String o = (String) it.next();
        }
    }
}

class Bar<CN extends Bar> {
    private List<CN> cns;

    /**
     * @deprecated
     */
    public Iterator<CN> <caret>iterator() {
        return getCns().iterator();
    }

    public List<CN> getCns() {
        if (cns == null) {
            return Collections.emptyList();
        }
        return cns;
    }
}