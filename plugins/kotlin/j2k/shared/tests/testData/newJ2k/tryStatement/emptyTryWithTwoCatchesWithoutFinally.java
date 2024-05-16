import java.io.IOException;

class C {
    void foo() {
        try {
        } catch (Exception e) {
            System.out.println(1);
        } catch (IOException e) {
            System.out.println(0);
        }
    }
}