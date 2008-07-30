/*
 * @author max
 */
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;

public class BaseFixture implements IdeaTestFixture {
  private Disposable myRootDisposable;

  public void setUp() throws Exception {
    myRootDisposable = new Disposable() {
      public void dispose() {
      }
    };
  }

  protected final <T extends Disposable> T disposeOnTearDown(T disposable) {
    Disposer.register(myRootDisposable, disposable);
    return disposable;
  }

  public void tearDown() throws Exception {
    if (myRootDisposable != null) {
      Disposer.dispose(myRootDisposable);
    }
    resetAllFields();
  }

  private void resetAllFields() {
    resetClassFields(getClass());
  }

  private void resetClassFields(final Class<?> aClass) {
    try {
      UsefulTestCase.clearDeclaredFields(this, aClass);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    if (aClass == BaseFixture.class) return;
    resetClassFields(aClass.getSuperclass());
  }

}