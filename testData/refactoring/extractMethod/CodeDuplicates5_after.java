class C {
    int myField = 10;
    int myOtherField = 10;

    {
        int i = 5;
        newMethod(i);

        C c = new C();

        c.newMethod(12);

        C c1 = new C();
        c1.myField = 12;
        myOtherField = 12;


        c.myField = 15;
        c1.myOtherField = 15;
    }

    private void newMethod(int i) {
        myField = i;
        myOtherField = i;
    }
}