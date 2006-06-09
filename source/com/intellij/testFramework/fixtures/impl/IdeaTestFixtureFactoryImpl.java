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

  private final InstanceFactory<IdeaProjectTestFixture> myFactory = new InstanceFactory<IdeaProjectTestFixture>();

  public IdeaTestFixtureFactoryImpl() {
    myFactory.registerType(IdeaProjectTestFixture.class, HeavyIdeaTestFixtureImpl.class);
    myFactory.registerType(CodeInsightTestFixture.class, CodeInsightTestFixtureImpl.class);
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl<IdeaProjectTestFixture>(new LightIdeaTestFixtureImpl());
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return new HeavyTestFixtureBuilderImpl<IdeaProjectTestFixture>(new HeavyIdeaTestFixtureImpl());
  }

  public <T extends IdeaProjectTestFixture> TestFixtureBuilder<T> createFixtureBuilder(Class<T> clazz) {
    T fixture = myFactory.getInstance(clazz);
    return new HeavyTestFixtureBuilderImpl<T>(fixture);
  }

  public TempDirTestFixture createTempDirTestFixture() {
    return new TempDirTextFixtureImpl();
  }
}
