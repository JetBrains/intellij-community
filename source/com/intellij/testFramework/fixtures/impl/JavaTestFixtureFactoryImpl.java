package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.StdModuleTypes;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.util.Factory;
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
    return new JavaCodeInsightTestFixtureImpl(projectFixture);
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    final Factory<Sdk> sdkFactory = new Factory<Sdk>() {
      public Sdk create() {
        return JavaSdkImpl.getMockJdk("java 1.4");
      }
    };
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl(sdkFactory, StdModuleTypes.JAVA));
  }

  public static class MyJavaModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl {
    public MyJavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }
}
