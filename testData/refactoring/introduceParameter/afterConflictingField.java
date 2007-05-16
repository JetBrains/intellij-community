public class Test {
    public int anObject;
    public void method(int anObject) {
    }
}

public class Test1 extends Test {
    public void method(int anObject) {
        System.out.println(anObject);
        System.out.println(this.anObject);
    }
}

public class Test2 extends Test1 {
    public void method(int anObject) {
        System.out.println(this.anObject);
    }
}

public class Usage {
    {
        Test t = new Test2();
        t.method(1 + 2);
    }
}