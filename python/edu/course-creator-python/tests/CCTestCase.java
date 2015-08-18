import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.jetbrains.python.PythonHelpersLocator;

public abstract class CCTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(
      getName());
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
  }

  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/edu/course-creator-python/testData";
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }
}
