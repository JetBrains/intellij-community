import java.util.ArrayList;

class B {
    I[] getA() { return null; };

    int method(ArrayList list) {
        I[] a = getA();

        for(i = 0; i < a.length; i++) {
            I item = a[i];
        }
    }
}