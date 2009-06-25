/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.EmptyModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * This is used in IdeaTestFixtureFactory
 * @author mike
 */
@SuppressWarnings({"UnusedDeclaration"})
public class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  protected final Map<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>> myFixtureBuilderProviders =
    new HashMap<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>>();

  public IdeaTestFixtureFactoryImpl() {
    registerFixtureBuilder(EmptyModuleFixtureBuilder.class, MyEmptyModuleFixtureBuilderImpl.class);
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

  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl(), myFixtureBuilderProviders);
  }

  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl(ourEmptyProjectDescriptor));
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@Nullable LightProjectDescriptor projectDescriptor) {
    if (projectDescriptor == null) {
      projectDescriptor = ourEmptyProjectDescriptor;
    }
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl(projectDescriptor));
  }

  public CodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new CodeInsightTestFixtureImpl(projectFixture);
  }

  public TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTestFixtureImpl();
  }

  public static class MyEmptyModuleFixtureBuilderImpl extends EmptyModuleFixtureBuilderImpl {
    public MyEmptyModuleFixtureBuilderImpl(final TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  private static final LightProjectDescriptor ourEmptyProjectDescriptor = new LightProjectDescriptor() {
    public ModuleType getModuleType() {
      return EmptyModuleType.getInstance();
    }

    public Sdk getSdk() {
      return null;
    }

    public void configureModule(Module module, ModifiableRootModel model) {
    }
  };
}
