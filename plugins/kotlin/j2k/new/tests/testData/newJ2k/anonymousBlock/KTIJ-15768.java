class C {
    int a = 10;

    C() {
        a = 12;
    }

    {
        a = 14;
    }

    public static void main(String[] args) {
        System.out.println(new C().a); // prints 12
    }
}