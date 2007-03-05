class A {
    A(){}    
    static class B {
        int k=0;
        {
            System.out.println(k);
        }
    }
    public static void main(String[] args) {
        new B();
    }
}

