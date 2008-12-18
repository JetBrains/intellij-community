package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public class XsltBasicTest extends TestBase {
    public void testSupportedXslt10() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedXslt11() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedSimplifiedXslt() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedXslt20() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedXsltNoVersion() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedNoXslt() throws Throwable {
        doTestXsltSupport();
    }

    private void doTestXsltSupport() throws Throwable {
        configure();
        final boolean b = XsltSupport.isXsltFile(myFixture.getFile());
        if (getName().contains("Unsupported")) {
            assertFalse(b);
        } else {
            assert getName().contains("Supported");
            assertTrue(b);
        }
    }

    private void configure() throws Throwable {
        myFixture.configureByFile(getTestFileName() + ".xsl");
    }

    protected String getSubPath() {
        return "xslt";
    }
}