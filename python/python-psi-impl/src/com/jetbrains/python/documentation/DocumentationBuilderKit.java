// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

final class DocumentationBuilderKit {
  public final static @NonNls String BR = "<br>";

  @NotNull
  static final Function<String, String> ESCAPE_ONLY = StringUtil::escapeXmlEntities;

  @NotNull
  static final Function<@NotNull String, @NotNull String> TO_ONE_LINE_AND_ESCAPE = s -> ESCAPE_ONLY.apply(s.replace('\n', ' '));

  @NotNull
  static final Function<String, String> ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES =
    s -> ESCAPE_ONLY.apply(s).replace("\n", BR).replace(" ", CommonXmlStrings.NBSP);

  @NotNull
  static final Function<String, String> WRAP_IN_ITALIC = s -> "<i>" + s + "</i>";

  @NotNull
  static final Function<String, String> WRAP_IN_BOLD = s -> "<b>" + s + "</b>";

  @NonNls
  static String combUp(@NonNls String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }
}
