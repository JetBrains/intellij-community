import java.util.*;
public class Test {

    String[] getArray(){
       return null;
    }

    void foo() {
        String[] array = getArray();
        Arrays.sort(array, new Comparator<String>() {
                    public int compare(String s1, String s2) {
                        return 0;
                    }
                });

    }

}
