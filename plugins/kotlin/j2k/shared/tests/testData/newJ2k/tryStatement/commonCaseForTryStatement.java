import java.io.IOException;

class C {
    void foo() {
        try {
            System.out.println();
        } catch (IOException e) {
            System.out.println(1);
        } catch (Exception e) {
            System.out.println(0);
        } finally {
            System.out.println(3);
        }
    }
}