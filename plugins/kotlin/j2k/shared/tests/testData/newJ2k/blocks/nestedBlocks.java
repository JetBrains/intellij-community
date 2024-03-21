class Foo {
    void x() {
        {
            {
                int a = 1;
                System.out.printf("%d\n", a);
            }
            {
                int a = 2;
                System.out.printf("%d\n", a);
            }
        }
    }
}