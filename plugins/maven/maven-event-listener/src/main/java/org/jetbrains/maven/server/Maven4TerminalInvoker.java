// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.maven.server;

import java.io.PrintWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.function.Consumer;

public class Maven4TerminalInvoker implements Consumer<String> {
  private final MethodHandle handle;
  private final Object terminal;

  public Maven4TerminalInvoker() throws Throwable {
    Class<?> messageUtilsClass = this.getClass().getClassLoader().loadClass("org.apache.maven.jline.MessageUtils");
    Method getTerminal = messageUtilsClass.getMethod("getTerminal");
    terminal = getTerminal.invoke(null);
    handle = MethodHandles.publicLookup().findVirtual(terminal.getClass(), "writer", MethodType.methodType(PrintWriter.class));
  }

  @Override
  public void accept(String s) {
    try {
      PrintWriter w = (PrintWriter)handle.invoke(terminal);
      w.println(s);
      w.flush();
    }
    catch (RuntimeException e) {
      throw e;
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }
}
