// INCLUDE_J2K_PREPROCESSOR_EXTENSIONS
public class Main {

    private int mCount;
    public void increment() {
        mCount++;
    }
    public static void doThing(int i) {
        throw new RuntimeException("oops");
    }
}