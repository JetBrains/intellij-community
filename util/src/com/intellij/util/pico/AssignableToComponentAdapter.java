package com.intellij.util.pico;

import org.picocontainer.ComponentAdapter;

public interface AssignableToComponentAdapter extends ComponentAdapter {
  boolean isAssignableTo(Class aClass);
}
