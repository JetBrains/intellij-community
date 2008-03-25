package com.intellij.lang.documentation;

/**
 * @author Dmitry Avdeev
 */
public class DocumentationUtil {
  @SuppressWarnings({"HardCodedStringLiteral"})
  public static void formatEntityName(String type, String name, StringBuilder destination) {
    destination.append(type).append(":&nbsp;<b>").append(name).append("</b><br>");
  }
}
