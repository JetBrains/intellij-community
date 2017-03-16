package com.intellij.debugger.streams.trace.impl.handler.type;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ClassTypeImpl extends GenericTypeImpl {
  public ClassTypeImpl(@NotNull String name) {
    super(name, name);
  }
}
