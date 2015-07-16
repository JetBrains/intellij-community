package com.jetbrains.rest.fixtures;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
@TestDataPath("$CONTENT_ROOT/../testData/rest")
public abstract class RestFixtureTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    myFixture.setTestDataPath(getTestDataPath());
  }

  protected String getTestDataPath() {
    return PythonHelpersLocator.getPythonCommunityPath() + "/python-rest/testData";
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
  }
}
