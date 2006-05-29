/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.fixtures.ModuleFixture;

/**
 * @author mike
 */
class ModuleFixtureImpl implements ModuleFixture {
  private Module myModule;
  private final ModuleFixtureBuilderImpl myBuilder;


  public ModuleFixtureImpl(final ModuleFixtureBuilderImpl builder) {
    myBuilder = builder;
  }

  public Module getModule() throws Exception {
    if (myModule != null) return myModule;
    myModule = myBuilder.buildModule();
    return myModule;
  }
}
