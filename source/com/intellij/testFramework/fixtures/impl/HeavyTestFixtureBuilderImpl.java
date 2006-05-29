/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import java.lang.reflect.Constructor;

/**
 * @author mike
*/
class HeavyTestFixtureBuilderImpl implements TestFixtureBuilder<IdeaProjectTestFixture> {
  private HeavyIdeaTestFixtureImpl myFixture;

  public TestFixtureBuilder<IdeaProjectTestFixture> setModuleType(final ModuleType moduleType) {
    throw new UnsupportedOperationException("setModuleType is not implemented in : " + getClass());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setLanguageLevel(final LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public IdeaProjectTestFixture getFixture() {
    if (myFixture == null) {
      myFixture = new HeavyIdeaTestFixtureImpl();
    }

    return myFixture;
  }

  public <M extends ModuleFixtureBuilder> M addModule(final Class<M> builderClass) {
    try {
      final Class aClass = IdeaTestFixtureFactoryImpl.ourBuilder2Implementation.get(builderClass);
      if (aClass == null)
        throw new IllegalArgumentException("Builder implementation not found:" + builderClass.getName());

      final Constructor constructor = aClass.getConstructor(TestFixtureBuilder.class);
      if (constructor != null) {
        return (M)constructor.newInstance(this);
      }

      //noinspection unchecked
      return (M)aClass.newInstance();
    }
    catch (Exception e) {
      throw new IllegalArgumentException("Can't instantiate builder: " + builderClass.getName(), e);
    }
  }
}
