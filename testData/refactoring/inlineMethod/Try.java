public class Try {
    public int test() {
        return another();
    }
 
    public int another<caret>() {
        try {
            return Integer.parseInt("1");
        }
        catch (NumberFormatException ex) {
            throw ex;
        }
    }
}
