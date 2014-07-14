import java.util.*;
public class Test {

    String[] getArray(){
       return null;
    }

    void foo() {
        String[] array = getArray();
        Arrays.binarySearch(array, "");
        Arrays.sort(array);
    }

}