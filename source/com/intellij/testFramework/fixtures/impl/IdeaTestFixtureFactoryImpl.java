/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * This is used in IdeaTestFixtureFactory
 * @author mike
 */
@SuppressWarnings({"UnusedDeclaration"})
public class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  private final Map<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>> myFixtureBuilderProviders =
    new HashMap<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>>();

  public IdeaTestFixtureFactoryImpl() {
    registerFixtureBuilder(JavaModuleFixtureBuilder.class, MyJavaModuleFixtureBuilderImpl.class);
  }

  public final <T extends ModuleFixtureBuilder> void registerFixtureBuilder(Class<T> aClass, Class<? extends T> implClass) {
    myFixtureBuilderProviders.put(aClass, implClass);
  }

  public void registerFixtureBuilder(Class<? extends ModuleFixtureBuilder> aClass, String implClassName) {
    try {
      final Class implClass = Class.forName(implClassName);
      assert aClass.isAssignableFrom(implClass);
      registerFixtureBuilder(aClass, implClass);
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException("Cannot instantiate fixture builder implementation", e);
    }
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl(), myFixtureBuilderProviders);
  }

  public CodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new CodeInsightTestFixtureImpl(projectFixture);
  }

  public TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTestFixtureImpl();
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
