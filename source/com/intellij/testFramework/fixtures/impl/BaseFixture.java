/*
 * @author max
 */
package com.intellij.testFramework.fixtures.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.IdeaTestFixture;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

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
    if (aClass == null) return;

    final Field[] fields = aClass.getDeclaredFields();
    for (Field field : fields) {
      final int modifiers = field.getModifiers();
      if ((modifiers & Modifier.FINAL) == 0
          &&  (modifiers & Modifier.STATIC) == 0
          && !field.getType().isPrimitive()) {
        field.setAccessible(true);
        try {
          field.set(this, null);
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
    }

    if (aClass == BaseFixture.class) return;
    resetClassFields(aClass.getSuperclass());
  }

}