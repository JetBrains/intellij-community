import java.util.*;

class Test
{
    void test(Map map) {

        Iterator<Map.Entry> entryIter = map.entrySet().iterator();
        while (entryIter.hasNext()) {
            Map.Entry entry = entryIter.next();
            List eventList = (List) entry.getValue();
        }
    }
}