/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.util.documentation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * @author Dennis.Ushakov
 */
public class MimeTypeDictionary {
  private static Set<String> ourContentTypes;

  public static Collection<String> getContentTypes() {
    if (ourContentTypes == null) {
      ourContentTypes = loadContentTypes();
    }
    return ourContentTypes;
  }

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

  private static void readMediaTypes(TreeSet<String> result, final String category) {
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
