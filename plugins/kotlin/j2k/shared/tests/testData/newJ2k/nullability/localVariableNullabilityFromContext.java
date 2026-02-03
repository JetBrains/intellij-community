// !SPECIFY_LOCAL_VARIABLE_TYPE_BY_DEFAULT: true
public class Foo {
    void test() {
        Integer i = new J().getInt(); // initialized as a platform type
        System.out.println(i + i); // used as a not-null type

        Integer j = new J().getInt();
        if (j != null) {
            System.out.println(j + j);
        }
    }
}