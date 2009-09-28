package org.intellij.lang.xpath;

public class XPathCompletionTest extends TestBase {

    public void testAxis() throws Throwable {
        doXPathCompletion("ancestor", "ancestor-or-self", "attribute");
    }

    public void testAxisInsert() throws Throwable {
        doXPathCompletion();
    }

    public void testPartialAxis() throws Throwable {
        doXPathCompletion();
    }

    public void testFunctions() throws Throwable {
        doXPathCompletion("text()", "translate(string, string, string)", "true()");
    }

    public void testFunctionInsert1() throws Throwable {
        doXPathCompletion();
    }

    public void testFunctionInsert2() throws Throwable {
        doXPathCompletion();
    }

    private void doXPathCompletion() throws Throwable {
        final String name = getTestFileName();
        myFixture.testCompletion(name + ".xpath", name + "_after.xpath");
    }

    private void doXPathCompletion(String... expectedVariants) throws Throwable {
        myFixture.testCompletionVariants(getTestFileName() + ".xpath", expectedVariants);
    }

    protected String getSubPath() {
        return "xpath/completion";
    }
}