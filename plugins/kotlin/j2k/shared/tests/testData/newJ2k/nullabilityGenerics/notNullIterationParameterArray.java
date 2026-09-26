public class C {
    public String[] stringsField = new String[]{"Hello", "World"};

    public void field() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    public void param(String[] strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }

    public void local() {
        String[] stringsLocal = new String[]{"Hello", "World"};
        for (String s : stringsLocal) {
            System.out.println(s.length());
        }
    }
}