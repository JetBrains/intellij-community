package com.jetbrains.python.fixtures;

import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.jetbrains.python.PythonMockSdk;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class PyLightFixtureTestCase extends UsefulTestCase {
  private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor();

  protected CodeInsightTestFixture myFixture;
  private static boolean ourPlatformPrefixInitialized;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initPlatformPrefix();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();

    myFixture.setTestDataPath(getTestDataPath());
  }

  protected String getTestDataPath() {
    return null;
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  @Nullable
  protected LightProjectDescriptor getProjectDescriptor() {
    return ourPyDescriptor;
  }

  protected static class PyLightProjectDescriptor implements LightProjectDescriptor {
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    public Sdk getSdk() {
      return PythonMockSdk.findOrCreate();
    }

    public void configureModule(Module module, ModifiableRootModel model, ContentEntry contentEntry) {
    }
  }

  public static void initPlatformPrefix() {
    if (!ourPlatformPrefixInitialized) {
      ourPlatformPrefixInitialized = true;
      boolean isIDEA = true;
      try {
        PyLightFixtureTestCase.class.getClassLoader().loadClass("com.intellij.openapi.project.impl.IdeaProjectManagerImpl");
      }
      catch (ClassNotFoundException e) {
        isIDEA = false;
      }
      if (!isIDEA) {
        System.setProperty("idea.platform.prefix", "Python");
      }
    }
  }
}
