class C {
    {
        a = 2;
    }


    int a = 4;

    int c = 4;

    {
        a++;
        b++;
    }

    {
        System.out.println(c);
        b = 2;
    }

    static {
        b++;
    }

    static int b = 0;
}

