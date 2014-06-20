import java.util.*;
public class Test {

    String[] getArray(){
       return null;
    }

    void foo() {
        String[] array = getArray();
        Collections.checkedList(array, String.class);
    }

}