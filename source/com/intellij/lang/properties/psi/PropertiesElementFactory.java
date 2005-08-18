package com.intellij.lang.properties.psi;

import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class PropertiesElementFactory {
  public static @NotNull Property createProperty(@NotNull Project project, @NotNull String name, @NotNull String value) {
    String text = escape(name) + "=" + value;
    final PropertiesFile dummyFile = createPropertiesFile(project, text);
    return dummyFile.getProperties().get(0);
  }

  public static PropertiesFile createPropertiesFile(final Project project, String text) {
    ParserDefinition def = StdFileTypes.PROPERTIES.getLanguage().getParserDefinition();
    String filename = "dummy." + StdFileTypes.PROPERTIES.getDefaultExtension();
    final PropertiesFile dummyFile = (PropertiesFile)def.createFile(project, filename, text);
    return dummyFile;
  }

  private static String escape(String name) {
    if (StringUtil.startsWithChar(name, '#')) {
      name = escapeChar(name, '#');
    }
    if (StringUtil.startsWithChar(name, '!')) {
      name = escapeChar(name, '!');
    }
    name = escapeChar(name, '=');
    name = escapeChar(name, ':');
    name = escapeChar(name, ' ');
    name = escapeChar(name, '\t');
    return name;
  }

  private static @NotNull String escapeChar(@NotNull String name, char c) {
    int offset = 0;
    while (true) {
      int i = name.indexOf(c, offset);
      if (i == -1) return name;
      if (i == 0 || name.charAt(i-1) != '\\') {
        name = name.substring(0, i) + '\\' + name.substring(i);
      }
      offset = i+2;
    }
  }
}
