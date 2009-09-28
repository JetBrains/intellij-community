package org.intellij.lang.xpath;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import junit.framework.TestCase;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 17.12.2008
*/
public abstract class TestBase extends TestCase {
    protected CodeInsightTestFixture myFixture;

    @Override
    protected void setUp() throws Exception {
        final JavaTestFixtureFactory factory = JavaTestFixtureFactory.getFixtureFactory();
        final IdeaProjectTestFixture fixture = factory.createLightFixtureBuilder().getFixture();
        myFixture = factory.createCodeInsightFixture(fixture);

        myFixture.setTestDataPath(getTestDataPath());

        myFixture.setUp();
    }

    private String getTestDataPath() {
        // path logic taken from RegExpSupport tests
        final String def = PathManager.getHomePath() + "/svnPlugins/xpath/xpath-lang/testData";
        return System.getProperty("idea.xpath.testdata-path", def) + "/" + getSubPath();
    }

    protected abstract String getSubPath();

    @Override
    protected void tearDown() throws Exception {
        myFixture.tearDown();
    }

    protected String getTestFileName() {
        final String s = getName().substring("test".length());
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }
}