class JavaClass extends Dva {
    @Override
    protected void justFun(int $this$justFun, String j) {
        System.out.println(j);
        System.out.println($this$justFun);
    }
}

class SecondJava extends JavaClass {
    @Override
    protected void justFun(int $this$justFun, String j2) {
        System.out.println(j2);
        System.out.println($this$justFun);
    }
}

class ThreeJava extends SecondJava {
    @Override
    protected void justFun(int $this$justFun, String j3) {
        System.out.println($this$justFun);
    }
}

class FourJava extends ThreeJava {
    @Override
    protected void justFun(int $this$justFun, String j4) {
        System.out.println($this$justFun);
        System.out.println(j4);
    }
}
