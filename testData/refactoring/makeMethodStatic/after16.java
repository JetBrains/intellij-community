public class Test {
    void anotherMethod(String s);
    String field;
    /**
     * @param anObject
     * @param field1
     */
    static void method(Test anObject, String field1) {
        anObject.anotherMethod(field1);
    }
}