package testPlugin;


import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.ComparingReferencesInspection;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import junit.framework.Assert;
import com.intellij.openapi.ui.Messages;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TestThisPlugin {

    protected CodeInsightTestFixture myFixture;
    // Specify path to your test data directory
    // e.g.  final String dataPath = "c:\\users\\john.doe\\idea\\community\\samples\\ComparingReferences/testData";
    final String dataPath = "c:\\users\\John.Doe\\idea\\community\\samples\\comparingReferences/testData";


    @Before

    public void setUp() throws Exception {

        final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
        final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
        myFixture = JavaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(testFixtureBuilder.getFixture());
        myFixture.setTestDataPath(dataPath);
        final JavaModuleFixtureBuilder builder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);

        builder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("");
        builder.setMockJdkLevel(JavaModuleFixtureBuilder.MockJdkLevel.jdk15);
        myFixture.setUp();


    }

    @After
    public void tearDown() throws Exception {
        myFixture.tearDown();
        myFixture = null;
    }

    protected void doTest(String testName, String hint) throws Throwable {
        myFixture.configureByFile(testName + ".java");
        myFixture.enableInspections(ComparingReferencesInspection.class);
        List<HighlightInfo> highlightInfos = myFixture.doHighlighting();
        Assert.assertTrue(!highlightInfos.isEmpty());

        final IntentionAction action = myFixture.findSingleIntention(hint);

        Assert.assertNotNull(action);
        myFixture.launchAction(action);
        myFixture.checkResultByFile(testName + ".after.java");

    }

    @Test
    // Test the "==" case
    public void test() throws Throwable {
        doTest("before", "Use equals()");

    }
     @Test
     // Test the "!=" case
    public void test1() throws Throwable {
        doTest("before1", "Use equals()");

    }

}
