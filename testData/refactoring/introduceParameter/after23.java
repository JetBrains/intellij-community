public class Test {
    public int i;
    public void method(int i) {
    }
}

public class Test1 extends Test {
    public void method(int i) {
        System.out.println(i);
        System.out.println(this.i);
    }
}

public class Test2 extends Test1 {
    public void method(int i) {
        System.out.println(this.i);
    }
}

public class Usage {
    {
        Test t = new Test2();
        t.method(1 + 2);
    }
}