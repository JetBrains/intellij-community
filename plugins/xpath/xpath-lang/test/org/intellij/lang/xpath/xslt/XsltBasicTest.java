package org.intellij.lang.xpath.xslt;

import com.intellij.psi.impl.PsiFileEx;
import com.intellij.psi.xml.XmlFile;

import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltChecker;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public class XsltBasicTest extends TestBase {
    public void testSupportedXslt10() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedXslt10_Loaded() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedXslt11() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedSimplifiedXslt() throws Throwable {
        doTestXsltSupport();
    }

    public void testSupportedSimplifiedXslt_Loaded() throws Throwable {
        doTestXsltSupport();
    }

    public void testPartialXslt20() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedXsltNoVersion() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedXsltNoVersion_Loaded() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedNoXslt() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedNoXslt_Loaded() throws Throwable {
        doTestXsltSupport();
    }

    public void testUnsupportedNoXslt2() throws Throwable {
        doTestXsltSupport();
    }

    // actually a PSI test: IDEADEV-35024
    public void testUnsupportedNoXslt2_Loaded() throws Throwable {
        doTestXsltSupport();
    }

    private void doTestXsltSupport() throws Throwable {
        configure();

        final XsltChecker.SupportLevel level = XsltSupport.getXsltSupportLevel(myFixture.getFile());
        switch (level) {
            case FULL:
                assertTrue(getName().contains("Supported"));
                assertTrue(XsltSupport.isXsltFile(myFixture.getFile()));
                break;
            case NONE:
                assertTrue(getName().contains("Unsupported"));
                assertFalse(XsltSupport.isXsltFile(myFixture.getFile()));
                break;
            case PARTIAL:
                assertTrue(getName().contains("Partial"));
                assertTrue(XsltSupport.isXsltFile(myFixture.getFile()));
                break;
        }
    }

    private void configure() throws Throwable {
        final String fileName = getTestFileName();
        myFixture.configureByFile(fileName.replaceAll("_.*$", "") + ".xsl");
        if (fileName.endsWith("_Loaded")) {
            ((XmlFile)myFixture.getFile()).getDocument();
            assertTrue(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
        } else {
            assertFalse(((PsiFileEx)myFixture.getFile()).isContentsLoaded());
        }
    }

    protected String getSubPath() {
        return "xslt";
    }
}
