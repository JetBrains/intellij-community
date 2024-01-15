class Test {
    static {
        System.out.println("0");
        a = 1;
    }

    static {
        System.out.println("1");
    }

    static int a;
    static int c = 0;

    static {
        System.out.println("2");
        System.out.println(c);
    }


    static int b;

    static {
        System.out.println("3");
        b = 2;
    }


    static {
        System.out.println("4");
        a = 2;
    }
}