import java.util.List;
public class Test {

    String[] getArray(){
       return null;
    }

    void foo() {
        String[] array = getArray();
        for (int i = 0; i < array.length; i++) {
           System.out.println(array[i]);
           array[i] = "";
        }
    }

}
