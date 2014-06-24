package com.jetbrains.python.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import com.jetbrains.python.psi.PyDecorator;
import org.jetbrains.annotations.NotNull;

/**
 * Python Decorator stub index.
 * Decorators are indexed by name
 * @author Ilya.Kazakevich
 */
public class PyDecoratorStubIndex extends StringStubIndexExtension<PyDecorator> {
  /**
   * Key to search for python decorators
   */
  public static final StubIndexKey<String, PyDecorator> KEY = StubIndexKey.createIndexKey("Python.Decorator");

  @NotNull
  @Override
  public StubIndexKey<String, PyDecorator> getKey() {
    return KEY;
  }
}
