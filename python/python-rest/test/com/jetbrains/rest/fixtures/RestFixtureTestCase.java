package com.jetbrains.rest.fixtures;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.jetbrains.python.fixtures.PyTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * User : catherine
 */
@TestDataPath("$CONTENT_ROOT/../testData/rest")
public abstract class RestFixtureTestCase extends UsefulTestCase {
  private static final RestDescriptor ourDescriptor = new RestDescriptor();

  protected CodeInsightTestFixture myFixture;
  private static boolean ourPlatformPrefixInitialized;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    PyTestCase.initPlatformPrefix();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    myFixture.setTestDataPath(getTestDataPath());
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/python/testData/rest";
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  @Nullable
  protected RestDescriptor getProjectDescriptor() {
    return ourDescriptor;
  }

  protected static class RestDescriptor implements LightProjectDescriptor {
    public RestDescriptor() {}

    @Override
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    @Override
    public Sdk getSdk() {
      return null;
    }

    @Override
    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }
  }
}
