// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.index;

import com.intellij.util.xml.NanoXmlBuilder;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public final class XsdTagNameBuilder implements NanoXmlBuilder {
  public static @NotNull Collection<String> computeTagNames(@NotNull Reader reader) {
    try {
      XsdTagNameBuilder builder = new XsdTagNameBuilder();
      NanoXmlUtil.parse(reader, builder);
      return builder.myTagNames;
    }
    finally {
      try {
        reader.close();
      }
      catch (IOException ignore) {
      }
    }
  }

  private final Collection<String> myTagNames = new ArrayList<>();
  private boolean myElementStarted;

  @Override
  public void startElement(@NonNls String name, @NonNls String nsPrefix, @NonNls String nsURI, String systemID, int lineNr) {
    myElementStarted = nsPrefix != null && nsURI.equals(XmlUtil.XML_SCHEMA_URI) && name.equals("element");
  }

  @Override
  public void addAttribute(@NonNls String key, String nsPrefix, String nsURI, String value, String type) {
    if (myElementStarted && key.equals("name")) {
      myTagNames.add(value);
      myElementStarted = false;
    }
  }
}
