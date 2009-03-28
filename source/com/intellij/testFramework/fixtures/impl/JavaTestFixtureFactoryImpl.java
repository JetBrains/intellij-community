package com.intellij.testFramework.fixtures.impl;

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

  public static class MyJavaModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl {
    public MyJavaModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }
}
