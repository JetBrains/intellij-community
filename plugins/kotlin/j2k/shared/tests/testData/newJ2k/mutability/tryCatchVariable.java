public class SomeClass {
    public void convertPatterns() {
        try {
            boolean ok;
            try {
                ok = doConvertPatterns();
            } catch (MalformedPatternException ignored) {
                ok = false;
            }
            if (!ok) {
                println("things are not okay");
            }
        } finally {
            myWildcardPatternsInitialized = true;
        }
    }
}