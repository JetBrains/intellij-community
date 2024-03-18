//file
public class NonDefault {
    public static void main(String[] args) {

        int value = 3;
        String valueString = "";
        int a = switch (value) {
        }
        System.out.println(valueString);
    }
}