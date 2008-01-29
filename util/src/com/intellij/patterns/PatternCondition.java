/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

/**
 * @author peter
*/
public abstract class PatternCondition<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.patterns.PatternCondition");
  @NonNls private static final String INIT = "<init>";
  @NonNls private static final String PARAMETER_FIELD_PREFIX = "val$";
  private String myMethodName;

  public PatternCondition() {
    final StackTraceElement[] trace = Thread.currentThread().getStackTrace();
    int i = 2;
    while (trace[i].getMethodName().equals(INIT)) i++;
    myMethodName = trace[i].getMethodName();
  }

  private void appendFieldValue(final StringBuilder builder, final Field field, String indent) {
    try {
      field.setAccessible(true);
      appendValue(builder, indent, field.get(this));
    }
    catch (IllegalAccessException e) {
      LOG.error(e);
    }
  }

  private static void appendValue(final StringBuilder builder, final String indent, final Object obj) {
    if (obj instanceof ElementPattern) {
      ((ElementPattern)obj).getCondition().append(builder, indent + "  ");
    } else if (obj instanceof Object[]) {
      builder.append("[");
      boolean first = true;
      for (final Object o : (Object[]) obj) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        appendValue(builder, indent, o);
      }
      builder.append("]");
    }
    else {
      builder.append(obj);
    }
  }

  public abstract boolean accepts(@NotNull T t, final MatchingContext matchingContext, @NotNull TraverseContext traverseContext);

  @NonNls
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    append(builder, "");
    return builder.toString();
  }

  public void append(StringBuilder builder, String indent) {
    builder.append(myMethodName);
    List<Field> params = ContainerUtil.findAll(getClass().getDeclaredFields(), new Condition<Field>() {
      public boolean value(final Field field) {
        return field.getName().startsWith(PARAMETER_FIELD_PREFIX);
      }
    });

    builder.append("(");
    if (params.size() == 1) {
      appendFieldValue(builder, params.get(0), indent);
    } else if (!params.isEmpty()) {
      boolean first = true;
      for (final Field field : params) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        builder.append(field.getName().substring(PARAMETER_FIELD_PREFIX.length())).append("=");
        appendFieldValue(builder, field, indent);
      }
    }
    builder.append(")");
  }

}
