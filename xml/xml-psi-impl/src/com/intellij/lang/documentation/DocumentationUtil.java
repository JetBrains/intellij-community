// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.documentation;

/**
 * @author Dmitry Avdeev
 */
public final class DocumentationUtil {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void formatEntityName(String type, String name, StringBuilder destination) {
    destination.append(type).append(":&nbsp;<b>").append(name).append("</b><br>");
  }
}
