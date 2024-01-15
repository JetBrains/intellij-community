class Test {
    public static int getInt(int i) {
        switch (i) {
            case 0:
                System.out.println(0);
                return 0;
            case 1:
                System.out.println(1);
                return 1;
            default:
                return -1;
        }
    }
}