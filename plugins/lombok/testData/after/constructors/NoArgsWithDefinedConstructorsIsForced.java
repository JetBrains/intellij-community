public class NoArgsWithDefinedConstructorsIsForced {
    private final String test;
    private final String test2;
    private final int test3;

    public NoArgsWithDefinedConstructorsIsForced(String param, String param2) {
        this.test = param;
        this.test2 = param2;
        this.test3 = 1;
    }

    public NoArgsWithDefinedConstructorsIsForced(String param) {
        this.test = param;
        this.test2 = param;
        this.test3 = 1;
    }

    public NoArgsWithDefinedConstructorsIsForced(String param, String param2, int param3) {
        this.test = param;
        this.test2 = param2;
        this.test3 = param3;
    }

    public static void main(String[] args) {
        final NoArgsWithDefinedConstructorsIsForced testClass = new NoArgsWithDefinedConstructorsIsForced();
        System.out.println(testClass);
    }

    public String getTest() {
        return this.test;
    }

    public String getTest2() {
        return this.test2;
    }

    public int getTest3() {
        return this.test3;
    }

    public NoArgsWithDefinedConstructorsIsForced() {
        this.test = null;
        this.test2 = null;
        this.test3 = 0;
    }
}
