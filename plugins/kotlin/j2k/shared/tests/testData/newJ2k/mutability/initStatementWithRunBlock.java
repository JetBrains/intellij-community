// IGNORE_K2
public class SomeClass {

    private final String aString;
    private final String bString;
    private final String cString;

    public SomeClass(String paramString) {
        {
            aString = "hello";
        }
        if (paramString == "String") {
            bString = "goodbye";
        }
        cString = paramString + "c";
    }
}