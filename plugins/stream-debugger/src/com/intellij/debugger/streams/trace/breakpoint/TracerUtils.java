// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.streams.trace.breakpoint;

import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Shumaf Lovpache
 */
public final class TracerUtils {
  private TracerUtils() { }

  @Nullable
  public static String tryExtractExceptionMessage(@NotNull ObjectReference exception) {
    final ReferenceType type = exception.referenceType();
    final Field messageField = type.fieldByName("detailMessage");
    if (messageField == null) return null;
    final Value message = exception.getValue(messageField);
    if (message instanceof StringReference) {
      return ((StringReference)message).value();
    }

    return null;
  }
}
