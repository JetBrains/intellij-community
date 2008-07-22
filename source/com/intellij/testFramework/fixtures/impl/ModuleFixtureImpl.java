/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.module.Module;
import com.intellij.testFramework.fixtures.ModuleFixture;

/**
 * @author mike
 */
public class ModuleFixtureImpl extends BaseFixture implements ModuleFixture {

  private Module myModule;
  protected final ModuleFixtureBuilderImpl myBuilder;

  public ModuleFixtureImpl(final ModuleFixtureBuilderImpl builder) {
    myBuilder = builder;
  }

  public Module getModule() {
    if (myModule != null) return myModule;
    myModule = myBuilder.buildModule();
    //disposeOnTearDown(myModule);
    return myModule;
  }

  public void setUp() throws Exception {
    super.setUp();
    getModule();
  }

  public void tearDown() throws Exception {
    myModule = null;
    super.tearDown();
  }
}
