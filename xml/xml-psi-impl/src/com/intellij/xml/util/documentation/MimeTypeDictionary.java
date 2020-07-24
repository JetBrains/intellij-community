// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util.documentation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtilRt;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Dennis.Ushakov
 */
public final class MimeTypeDictionary {
  public static final String[] HTML_CONTENT_TYPES = ArrayUtilRt.toStringArray(loadContentTypes());

  private static Set<String> loadContentTypes() {
    final TreeSet<String> result = new TreeSet<>();
    result.add("*/*");
    // IANA Media Types: http://www.iana.org/assignments/media-types/media-types.xhtml
    readMediaTypes(result, "application");
    readMediaTypes(result, "audio");
    readMediaTypes(result, "image");
    readMediaTypes(result, "message");
    readMediaTypes(result, "model");
    readMediaTypes(result, "multipart");
    readMediaTypes(result, "text");
    readMediaTypes(result, "video");
    return result;
  }

  private static void readMediaTypes(TreeSet<? super String> result, final String category) {
    final InputStream stream = MimeTypeDictionary.class.getResourceAsStream("mimeTypes/" + category + ".csv");
    String csv = "";
    try {
      csv = stream != null ? FileUtil.loadTextAndClose(stream) : "";
    }
    catch (IOException e) {
      Logger.getInstance(MimeTypeDictionary.class).error(e);
    }
    final String[] lines = StringUtil.splitByLines(csv);
    for (String line : lines) {
      if (line == lines[0]) continue;

      final String[] split = line.split(",");
      if (split.length > 1) {
        result.add(!split[1].isEmpty() ? split[1] : withCategory(category, split[0]));
      }
    }
  }

  private static String withCategory(String category, String name) {
    final int whitespacePosition = name.indexOf(' ');
    return category + "/" + (whitespacePosition > 0 ? name.substring(0, whitespacePosition) : name);
  }
}
