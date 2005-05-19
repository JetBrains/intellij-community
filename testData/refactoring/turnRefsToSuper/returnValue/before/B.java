import java.util.ArrayList;

class B {
    A[] getA() { return null; };

    int method(ArrayList list) {
        A[] a = getA();

        for(i = 0; i < a.length; i++) {
            A item = a[i];
        }
    }
}