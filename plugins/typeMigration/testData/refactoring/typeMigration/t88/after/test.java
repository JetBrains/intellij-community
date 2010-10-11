import java.util.*;
public class Test {

    List<String> getArray(){
       return null;
    }

    void foo() {
        List<String> array = getArray();
        Collections.sort(array, new Comparator<String>() {
            public int compare(String s1, String s2) {
                return 0;
            }
        });

    }

}
