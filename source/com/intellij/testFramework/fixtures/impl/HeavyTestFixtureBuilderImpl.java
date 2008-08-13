/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.HeavyIdeaTestFixture;
import com.intellij.util.containers.FactoryMap;
import com.intellij.util.pico.IdeaPicoContainer;
import org.picocontainer.MutablePicoContainer;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * @author mike
*/
class HeavyTestFixtureBuilderImpl implements TestFixtureBuilder<IdeaProjectTestFixture> {
  private final FactoryMap<Class<? extends ModuleFixtureBuilder>, ModuleFixtureBuilder> myModuleFixtureBuilderFactory;

  private HeavyIdeaTestFixtureImpl myFixture;

  public HeavyTestFixtureBuilderImpl(HeavyIdeaTestFixtureImpl fixture, final Map<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>> providers) {
    myFixture = fixture;

    final MutablePicoContainer container = new IdeaPicoContainer();
    container.registerComponentInstance(this);

    myModuleFixtureBuilderFactory = new FactoryMap<Class<? extends ModuleFixtureBuilder>, ModuleFixtureBuilder>() {
      protected ModuleFixtureBuilder create(Class<? extends ModuleFixtureBuilder> key) {
        Class<? extends ModuleFixtureBuilder> implClass = providers.get(key);
        container.registerComponentImplementation(implClass);
        return (ModuleFixtureBuilder)container.getComponentInstanceOfType(implClass);
      }
    };
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setModuleType(final ModuleType moduleType) {
    new Pair<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>>(JavaModuleFixtureBuilder.class, JavaModuleFixtureBuilderImpl.class);
    throw new UnsupportedOperationException("setModuleType is not implemented in : " + getClass());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setLanguageLevel(final LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public HeavyIdeaTestFixture getFixture() {
    return myFixture;
  }

  public <M extends ModuleFixtureBuilder> M addModule(final Class<M> builderClass) {
    loadClassConstants(builderClass);
    final M builder = (M)myModuleFixtureBuilderFactory.get(builderClass);
    myFixture.addModuleFixtureBuilder(builder);
    return builder;
  }

  private static void loadClassConstants(final Class builderClass) {
    try {
      for (final Field field : builderClass.getFields()) {
        field.get(null);
      }
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }
}
