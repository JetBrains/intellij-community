class Test {
    {
        System.out.println("0");
        a = 1;
    }

    {
        System.out.println("1");
    }

    int a;
    int c = 0;

    {
        System.out.println("2");
        System.out.println(c);
    }


    int b;
    {
        System.out.println("3");
        b = 2;
    }


    {
        System.out.println("4");
        a = 2;
    }

}