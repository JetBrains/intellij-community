public class Try {
    public int test() {
        return another();
    }
 
    public synchronized int another<caret>() {
        try {
            return Integer.parseInt("1");
        }
        catch (NumberFormatException ex) {
            throw ex;
        }
    }
}
