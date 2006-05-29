/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.builders.WebModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * This is used in IdeaTestFixtureFactory
 * @author mike
 */
public class IdeaTestFixtureFactoryImpl extends IdeaTestFixtureFactory {
  static final Map<Class, Class> ourBuilder2Implementation = new HashMap<Class, Class>();

  static {
    ourBuilder2Implementation.put(JavaModuleFixtureBuilder.class, JavaModuleFixtureBuilderImpl.class);
    ourBuilder2Implementation.put(WebModuleFixtureBuilder.class, WebModuleFixtureBuilderImpl.class);
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder() {
    return new LightTestFixtureBuilderImpl();
  }

  public TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return new HeavyTestFixtureBuilderImpl();
  }

}
