package org.intellij.lang.xpath.xslt;

import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

import com.intellij.util.ArrayUtil;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 12.06.2008
*/
public class XsltHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new XsltStuffProvider(null).getInspectionClasses());
    }

    public void xtestBackwardIncludedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownTemplate() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnknownMode() throws Throwable {
        doXsltHighlighting();
    }

    public void testUndeclaredParam() throws Throwable {
        doXsltHighlighting();
    }

    public void testMissingParam() throws Throwable {
        doXsltHighlighting();
    }

    public void testUnusedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testShadowedVariable() throws Throwable {
        doXsltHighlighting();
    }

    public void testValidPatterns() throws Throwable {
        doXsltHighlighting();
    }

    public void testInValidPattern1() throws Throwable {
        doXsltHighlighting();
    }

    public void testInValidPattern2() throws Throwable {
        doXsltHighlighting();
    }

    public void testInValidPattern3() throws Throwable {
        doXsltHighlighting();
    }

    public void testInValidPattern4() throws Throwable {
        doXsltHighlighting();
    }

    public void testInValidPattern5() throws Throwable {
        doXsltHighlighting();
    }

    private void doXsltHighlighting(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xsl"));
    }

    protected String getSubPath() {
        return "xslt/highlighting";
    }
}