import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
@interface Ann1 {
}

@Target({ElementType.LOCAL_VARIABLE, ElementType.PARAMETER})
@interface Ann2 {
}

public class C {
    public void f1() {
        @Ann1 int control = 0;
        @Ann1 int[] arr = {1, 2, 3};
        for (@Ann1 int test : arr) {
            System.out.println(control + test);
        }
        for (@Ann1 int i = 0; i < arr.length; i++) {
            System.out.println(control + arr[i]);
        }
    }
    public void f2() {
        @Ann1 @Ann2 int control = 0;
        @Ann1 @Ann2 int[] arr = {1, 2, 3};
        for (@Ann1 @Ann2 int test : arr) {
            System.out.println(control + test);
        }
        for (@Ann1 @Ann2 int i = 0; i < arr.length; i++) {
            System.out.println(control + arr[i]);
        }
    }
}