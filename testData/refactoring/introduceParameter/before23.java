public class Test {
    public int i;
    public void method() {
    }
}

public class Test1 extends Test {
    public void method() {
        System.out.println(<selection>1 + 2</selection>);
        System.out.println(i);
    }
}

public class Test2 extends Test1 {
    public void method() {
        System.out.println(i);
    }
}

public class Usage {
    {
        Test t = new Test2();
        t.method();
    }
}