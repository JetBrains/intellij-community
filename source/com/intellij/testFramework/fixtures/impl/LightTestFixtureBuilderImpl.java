/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

/**
 * @author mike
*/
class LightTestFixtureBuilderImpl implements TestFixtureBuilder<IdeaProjectTestFixture> {
  public TestFixtureBuilder<IdeaProjectTestFixture> setModuleType(final ModuleType moduleType) {
    throw new UnsupportedOperationException("setModuleType is not implemented in : " + getClass());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> setLanguageLevel(final LanguageLevel languageLevel) {
    throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
  }

  public IdeaProjectTestFixture getFixture() {
    return new LightIdeaTestFixtureImpl();
  }

  public <M extends ModuleFixtureBuilder> M addModule(final Class<M> builderClass) {
    throw new UnsupportedOperationException("addModule is not allowed in : " + getClass());
  }
}
