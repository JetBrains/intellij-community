import java.util.List;
import java.util.ArrayList;

class Clazz {
    List<? extends Class<?>> l = new ArrayList<Class<?>>();
    Class<?> o = String.class;
    int i = l.indexOf(o);
}