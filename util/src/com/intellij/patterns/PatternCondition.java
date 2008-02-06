/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.patterns;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
*/
public abstract class PatternCondition<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.patterns.PatternCondition");
  @NonNls private static final String INIT = "<init>";
  @NonNls private static final String PARAMETER_FIELD_PREFIX = "val$";
  private final String myMethodName;

  public PatternCondition(@NonNls String methodName) {
    myMethodName = methodName;
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
      appendArray(builder, indent, (Object[])obj);
    } else if (obj instanceof Collection) {
      appendArray(builder, indent, ((Collection) obj).toArray());
    }
    else {
      builder.append(obj);
    }
  }

  protected static void appendArray(final StringBuilder builder, final String indent, final Object[] objects) {
    builder.append("[");
    boolean first = true;
    for (final Object o : objects) {
      if (!first) {
        builder.append(", ");
      }
      first = false;
      appendValue(builder, indent, o);
    }
    builder.append("]");
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
    builder.append("(");
    appendParams(builder, indent);
    builder.append(")");
  }

  private void appendParams(final StringBuilder builder, final String indent) {
    List<Field> params = new SmartList<Field>();
    for (Class aClass = getClass(); aClass != null; aClass = aClass.getSuperclass()) {
      for (final Field field : aClass.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) &&
            (((field.getModifiers() & 0x1000) == 0 && !aClass.equals(PatternCondition.class)) || field.getName().startsWith(PARAMETER_FIELD_PREFIX))) {
          params.add(field);
        }
      }
    }

    if (params.size() == 1) {
      appendFieldValue(builder, params.get(0), indent);
    } else if (!params.isEmpty()) {
      boolean first = true;
      for (final Field field : params) {
        if (!first) {
          builder.append(", ");
        }
        first = false;
        String name = field.getName();
        if (name.startsWith(PARAMETER_FIELD_PREFIX)) name = name.substring(PARAMETER_FIELD_PREFIX.length());
        builder.append(name).append("=");
        appendFieldValue(builder, field, indent);
      }
    }
  }

}
