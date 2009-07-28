package com.jetbrains.python.fixtures;

import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
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
public class PyLightFixtureTestCase extends UsefulTestCase {
  private static final PyLightProjectDescriptor ourPyDescriptor = new PyLightProjectDescriptor();

  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(getProjectDescriptor());
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture, new LightTempDirTestFixtureImpl());
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

  private static class PyLightProjectDescriptor implements LightProjectDescriptor {
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    public Sdk getSdk() {
      return PythonMockSdk.findOrCreate();
    }

    public void configureModule(Module module, ModifiableRootModel model) {
    }
  }

}
