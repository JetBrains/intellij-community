package com.intellij.execution.testframework;




public interface TestFrameworkPropertyListener<T> {
  void onChanged(T value);
}
