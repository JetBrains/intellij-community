package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

/**
 * @author yole
 */
public class JavaTestFixtureFactoryImpl extends JavaTestFixtureFactory {
  public JavaTestFixtureFactoryImpl() {
    IdeaTestFixtureFactoryImpl.getFixtureFactory().registerFixtureBuilder(JavaModuleFixtureBuilder.class, MyJavaModuleFixtureBuilderImpl.class);
  }

  public JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, new TempDirTestFixtureImpl());
  }

  @Override
  public JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl(ourJavaProjectDescriptor));
  }

  public static class MyJavaModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl {
    public MyJavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  private static final LightProjectDescriptor ourJavaProjectDescriptor = new LightProjectDescriptor() {
    public ModuleType getModuleType() {
      return StdModuleTypes.JAVA;
    }

    public Sdk getSdk() {
      return JavaSdkImpl.getMockJdk("java 1.4");
    }

    public void configureModule(Module module, ModifiableRootModel model) {
    }
  };
}
