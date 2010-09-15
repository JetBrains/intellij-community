package testPlugin;

import com.intellij.codeInsight.intention.IntentionAction;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Chursin
 * Date: Sep 13, 2010
 * Time: 9:35:50 PM
 * To change this template use File | Settings | File Templates.
 */

public class YourTest {
    protected CodeInsightTestFixture myFixture;
    // Specify path to your test data
    // e.g.  final String dataPath = "c:\\users\\john.doe\\idea\\community\\samples\\conditionalOperatorConvertor/testData";
    final String dataPath = "c:\\users\\FirstName.LastName\\idea\\community\\samples\\conditionalOperatorConvertor/testData";

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
        // Messages.showInfoMessage("Test started", "Info");
        myFixture.configureByFile(testName + ".java");
        final IntentionAction action = myFixture.findSingleIntention(hint);
        Assert.assertNotNull(action);
        myFixture.launchAction(action);
        myFixture.checkResultByFile(testName + ".after.java");
    }

    @Test
    public void test() throws Throwable {
        doTest("before.template", "Convert ternary operator to if statement");
    }

}
