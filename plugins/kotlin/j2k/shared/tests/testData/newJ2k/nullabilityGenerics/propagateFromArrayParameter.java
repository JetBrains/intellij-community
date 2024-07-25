public class C {
    public void foo(String[] strings) {
        bar(strings);
    }

    public void bar(String[] strings) {
        baz(strings);
    }

    public void baz(String[] strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }
}