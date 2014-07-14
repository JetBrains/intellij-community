public class Test {

    String str;
    String get(boolean f) {
        return f ? str : str + str;
    }

}
