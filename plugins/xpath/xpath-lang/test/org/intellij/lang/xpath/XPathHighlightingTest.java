package org.intellij.lang.xpath;

import com.intellij.util.ArrayUtil;

public class XPathHighlightingTest extends TestBase {
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new XPathSupportLoader().getInspectionClasses());
    }

    public void testPathTypeMismatch() throws Throwable {
        doXPathHighlighting();
    }

    public void testUnknownFunction() throws Throwable {
        doXPathHighlighting();
    }

    public void testMissingArgument() throws Throwable {
        doXPathHighlighting();
    }

    public void testInvalidArgument() throws Throwable {
        doXPathHighlighting();
    }

    public void testIndexZero() throws Throwable {
        doXPathHighlighting();
    }

    public void testSillyStep() throws Throwable {
        doXPathHighlighting();
    }

    public void testNonSillyStepIDEADEV33539() throws Throwable {
        doXPathHighlighting();
    }

    public void testHardwiredPrefix() throws Throwable {
        doXPathHighlighting();
    }

    private void doXPathHighlighting(String... moreFiles) throws Throwable {
        final String name = getTestFileName();
        myFixture.testHighlighting(true, false, false, ArrayUtil.append(moreFiles, name + ".xpath"));
    }

    protected String getSubPath() {
        return "xpath/highlighting";
    }
}
