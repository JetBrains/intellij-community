/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.fixtures.*;

/**
 *
 * This is used in IdeaTestFixtureFactory
 * @author mike
 */
public class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return new HeavyTestFixtureBuilderImpl(new HeavyIdeaTestFixtureImpl());
  }

  public CodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new CodeInsightTestFixtureImpl(projectFixture);
  }

  public TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTextFixtureImpl();
  }
}
