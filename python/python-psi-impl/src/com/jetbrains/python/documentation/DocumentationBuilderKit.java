// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xml.CommonXmlStrings;
import com.intellij.xml.util.XmlStringUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

class DocumentationBuilderKit {
  static final TagWrapper TagBold = new TagWrapper("b");
  static final TagWrapper TagSmall = new TagWrapper("small");
  static final TagWrapper TagCode = new TagWrapper("code");
  static final TagWrapper TagSpan = new TagWrapper("span");

  public final static @NonNls String BR = "<br>";

  @NotNull
  static final Function<String, String> ESCAPE_ONLY = StringUtil::escapeXmlEntities;

  @NotNull
  static final Function<String, String> TO_ONE_LINE_AND_ESCAPE = s -> ESCAPE_ONLY.apply(s.replace('\n', ' '));

  @NotNull
  static final Function<String, String> ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES =
    s -> ESCAPE_ONLY.apply(s).replace("\n", BR).replace(" ", CommonXmlStrings.NBSP);

  @NotNull
  static final Function<String, String> WRAP_IN_ITALIC = s -> "<i>" + s + "</i>";

  @NotNull
  static final Function<String, String> WRAP_IN_BOLD = s -> "<b>" + s + "</b>";

  private DocumentationBuilderKit() {
  }

  static ChainIterable<String> wrapInTag(String tag, Iterable<String> content) {
    return new ChainIterable<>("<" + tag + ">").add(content).addItem("</" + tag + ">");
  }

  static ChainIterable<String> wrapInTag(String tag, List<? extends Pair<String, String>> attributes, Iterable<String> content) {
    if (attributes.size() == 0) {
      return wrapInTag(tag, content);
    } else {
      StringBuilder s = new StringBuilder("<" + tag);
      for (Pair<String, String> attr: attributes) {
        s.append(" ").append(attr.first).append("=\"").append(attr.second).append("\"");
      }
      s.append(">");
      return new ChainIterable<>(s.toString()).add(content).addItem("</" + tag + ">");
    }
  }

  @NonNls
  static String combUp(@NonNls String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }

  static ChainIterable<String> $(String... content) {
    return new ChainIterable<>(Arrays.asList(content));
  }

  // make a first-order curried objects out of wrapInTag()
  static class TagWrapper implements Function<Iterable<String>, Iterable<String>> {
    private final String myTag;
    private final List<Pair<String, String>> myAttributes = new ArrayList<>();

    TagWrapper(String tag) {
      myTag = tag;
    }

    public TagWrapper withAttribute(String name, String value) {
      TagWrapper result = new TagWrapper(myTag);
      result.myAttributes.addAll(myAttributes);
      result.myAttributes.add(Pair.create(name, value));
      return result;
    }

    @Override
    public Iterable<String> apply(Iterable<String> contents) {
      return wrapInTag(myTag, myAttributes, contents);
    }

  }
}
