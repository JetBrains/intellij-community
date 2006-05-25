/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.ModuleType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

/**
 *
 * This is used in IdeaTestFixtureFactory
 * @author mike
 */
public class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new TestFixtureBuilder<IdeaProjectTestFixture>() {
      public IdeaProjectTestFixture setModuleType(final ModuleType moduleType) {
        throw new UnsupportedOperationException("setModuleType is not implemented in : " + getClass());
      }

      public IdeaProjectTestFixture setLanguageLevel(final LanguageLevel languageLevel) {
        throw new UnsupportedOperationException("setLanguageLevel is not implemented in : " + getClass());
      }

      public IdeaProjectTestFixture getFixture() {
        return new LightIdeaTestFixtureImpl();
      }
    };
  }
}
