class Test {
    public static String bar(int x , int y) {
        while (true) {
            newMethod(x == y);
        }
    }

    private static void newMethod(boolean b) {
        if (b) {
            return;
        }
        System.out.println("HW");
    }

    private static int g() {
        return 0;
    }
}
