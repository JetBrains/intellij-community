public class NonDefault {
    public static void main(String[] args) {

        int value = 3;
        String valueString = "";
        int a = switch (value) {
            case 1:  valueString = "ONE";      yield 1;
            case 2:  valueString = "TWO";      yield 2;
            case 3:  valueString = "THREE";    yield 3;
        }
        System.out.println(valueString);
    }
}