package com.intellij.debugger.streams.trace.smart.handler.type;

import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ClassTypeImpl extends GenericTypeImpl {
  public ClassTypeImpl(@NotNull String name) {
    super(name, name);
  }
}
