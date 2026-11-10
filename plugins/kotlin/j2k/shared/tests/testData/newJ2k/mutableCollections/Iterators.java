import java.util.*;

class A {
    void setThroughListIterator(List<String> list) {
        ListIterator<String> iterator = list.listIterator();
        iterator.next();
        iterator.set("a");
    }

    void removeInLoop(List<String> list) {
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext(); ) {
            if (iterator.next().isEmpty()) iterator.remove();
        }
    }

    void readInLoop(List<String> list) {
        for (Iterator<String> iterator = list.iterator(); iterator.hasNext(); ) {
            System.out.println(iterator.next());
        }
    }

    void removeThroughReturnedIterator(List<String> list) {
        Iterator<String> iterator = iteratorOf(list);
        iterator.next();
        iterator.remove();
    }

    private Iterator<String> iteratorOf(List<String> list) {
        return list.iterator();
    }
}
