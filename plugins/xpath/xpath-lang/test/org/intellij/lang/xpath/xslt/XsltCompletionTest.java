package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class XsltCompletionTest extends TestBase {

    public void testLocalVariable() throws Throwable {
        doXsltCompletion();
    }

    public void testGlobalVariable() throws Throwable {
        doXsltCompletion();
    }

    public void testTemplates() throws Throwable {
        doXsltCompletion();
    }

    public void testModes() throws Throwable {
        doXsltCompletion();
    }

    public void testNamedTemplateParams() throws Throwable {
        doXsltCompletion();
    }

    public void testIncludedTemplateParam() throws Throwable {
        doXsltCompletion("included.xsl");
    }

    public void testApplyTemplateParams() throws Throwable {
        doXsltCompletion();
    }

    public void testIncludedVariable() throws Throwable {
        doXsltCompletion("included.xsl");
    }

    public void testUsedNames() throws Throwable {
        doXsltCompletion();
    }

    private void doXsltCompletion(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testCompletion(name + ".xsl", name + "_after.xsl", moreFiles);
    }

    protected String getSubPath() {
        return "xslt/completion";
    }
}