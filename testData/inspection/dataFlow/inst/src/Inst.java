class A {

}

class B extends A {

}

public class Inst {
    public void x() {
        Object a = new Object();


        if (a instanceof B) {
            A aa =(A) a;
            if (a instanceof A) {
                System.out.println("HeHe");
            }
            System.out.println(aa);
        }
    }

    public void y(Object a) {
        if (a instanceof A) {}
        if (a instanceof B) {}
    }
}
