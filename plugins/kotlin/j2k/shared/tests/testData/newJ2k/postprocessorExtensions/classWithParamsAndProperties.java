// J2K_POSTPROCESSOR_EXTENSIONS
public class Main {

    private String mName;
    private int mCount;

    public Main(String name) {
        this.mName = name;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public void increment() {
        mCount++;
    }
    public static void main(String[] a) {
        System.out.println("Hello world!");
    }
    public static void doThing(int i) {
        throw new RuntimeException("oops");
    }
}