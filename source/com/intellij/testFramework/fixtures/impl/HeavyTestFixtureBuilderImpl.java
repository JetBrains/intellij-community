/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.Pair;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.builders.WebModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import java.util.Map;
import java.util.HashMap;

/**
 * @author mike
*/
class HeavyTestFixtureBuilderImpl<T extends IdeaProjectTestFixture> implements TestFixtureBuilder<T> {

  private final Map<Class<? extends ModuleFixtureBuilder>, ModuleFixtureBuilder> myModuleFixtureBuilderFactory = new HashMap<Class<? extends ModuleFixtureBuilder>, ModuleFixtureBuilder>();

  private T myFixture;

  public HeavyTestFixtureBuilderImpl(T fixture) {
    myFixture = fixture;

    myModuleFixtureBuilderFactory.put(JavaModuleFixtureBuilder.class, new JavaModuleFixtureBuilderImpl(this));
    myModuleFixtureBuilderFactory.put(WebModuleFixtureBuilder.class, new WebModuleFixtureBuilderImpl(this));
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setModuleType(final ModuleType moduleType) {
    new Pair<Class<? extends ModuleFixtureBuilder>, Class<? extends ModuleFixtureBuilder>>(JavaModuleFixtureBuilder.class, JavaModuleFixtureBuilderImpl.class);
    throw new UnsupportedOperationException("setModuleType is not implemented in : " + getClass());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setLanguageLevel(final LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public T getFixture() {
    return myFixture;
  }

  public <M extends ModuleFixtureBuilder> M addModule(final Class<M> builderClass) {
    return (M)myModuleFixtureBuilderFactory.get(builderClass);
  }
}
