class Foo {
    private static String s;
    private static Integer i;
    private static Character c;

    public static void main(String[] args) {
        System.out.println("5" + 1);
        System.out.println(1 + "5");
        System.out.println(1 + 3 + "5");
        System.out.println((1 + 3) + "5");
        System.out.println("5" + "5" + (1 + 3));
        System.out.println("5" + "5" + 1 + 3);
        System.out.println("5" + "5" + 1);
        System.out.println("5" + ("5" + 1) + 3);
        System.out.println("5" + ("5" + 1) + 3 + 4);
        System.out.println(1 + "3" + 4 + "5");
        System.out.println(1 + 3 + 4 + "5");
        System.out.println("5" + 1 + 3 + 4);
        System.out.println('c' + "5");
        System.out.println('c' + 'd' + "5");
        System.out.println("5" + 'c');
        System.out.println("5" + 'c' + 'd');
        System.out.println(c + "s");
        System.out.println(c + "s" + c);
        System.out.println("s" + c + c);
        System.out.println(s + 'c');
        System.out.println(s + 'c' + 'd');
        System.out.println('c' + s);
        System.out.println(s + null);
        System.out.println(null + s);
        System.out.println(i + "s");
        System.out.println(i + "s" + i);
        System.out.println("s" + i + i);
        System.out.println(null + "s");
        System.out.println("s" + null);
        System.out.println("s" + null + null);
        Object o = new Object();
        System.out.println(o + "");
        System.out.println("" + o);
        System.out.println(o.hashCode() + "");
        System.out.println("" + o.hashCode());
        String[] bar = {"hi"};
        System.out.println(1 + bar[0]);
        System.out.println(1 + 2 + bar[0]);
        System.out.println(bar[0] + 1);
        System.out.println(bar[0] + 1 + 2);
    }
}