package pack1;

import java.util.ArrayList;
import java.util.List;

public class A {
    public static void foo(){
        Outer<ArrayList>.Inner<List> x;
    }
}