/*
 * @author max
 */
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;
import junit.framework.Assert;

public class BaseFixture implements IdeaTestFixture {
  private Disposable myRootDisposable;
  private boolean myDisposed;

  public void setUp() throws Exception {
    Assert.assertNull("setUp() already has been called", myRootDisposable);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
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
    Assert.assertNotNull("setUp() has not been called", myRootDisposable);
    Assert.assertFalse("tearDown() already has been called", myDisposed);
    myDisposed = true;
    Disposer.dispose(myRootDisposable);
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