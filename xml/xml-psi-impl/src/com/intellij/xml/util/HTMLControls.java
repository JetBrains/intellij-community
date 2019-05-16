// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import com.intellij.util.xmlb.Converter;
import com.intellij.util.xmlb.XmlSerializer;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public class HTMLControls {
  private static final Logger LOG = Logger.getInstance(HTMLControls.class);
  private static Control[] ourControls;

  public static Control[] getControls() {
    if (ourControls == null) {
      ourControls = loadControls();
    }
    return ourControls;
  }

  private static Control[] loadControls() {
    Element element;
    try {
      // use temporary bytes stream because otherwise inputStreamSkippingBOM will fail
      // on ZipFileInputStream used in jar files
      final byte[] bytes;
      try (final InputStream stream = HTMLControls.class.getResourceAsStream("HtmlControls.xml")) {
        bytes = FileUtilRt.loadBytes(stream);
      }
      try (final UnsyncByteArrayInputStream bytesStream = new UnsyncByteArrayInputStream(bytes)) {
        element = JDOMUtil.load(CharsetToolkit.inputStreamSkippingBOM(bytesStream));
      }
    }
    catch (Exception e) {
      LOG.error(e);
      return new Control[0];
    }
    if (!element.getName().equals("htmlControls")) {
      LOG.error("HTMLControls storage is broken");
      return new Control[0];
    }
    return XmlSerializer.deserialize(element, Control[].class);
  }

  public enum TagState { REQUIRED, OPTIONAL, FORBIDDEN }

  @Tag("control")
  public static class Control {
    @Attribute("name")
    public String name;
    @Attribute(value = "startTag", converter = TagStateConverter.class)
    public TagState startTag;
    @Attribute(value = "endTag", converter = TagStateConverter.class)
    public TagState endTag;
    @Attribute("emptyAllowed")
    public boolean emptyAllowed;
    @Attribute(value = "autoClosedBy", converter = AutoCloseConverter.class)
    public Set<String> autoClosedBy = Collections.emptySet();
  }

  private static class TagStateConverter extends Converter<TagState> {
    @Nullable
    @Override
    public TagState fromString(@NotNull String value) {
      return TagState.valueOf(StringUtil.toUpperCase(value));
    }

    @NotNull
    @Override
    public String toString(@NotNull TagState state) {
      return StringUtil.toLowerCase(state.name());
    }
  }

  private static class AutoCloseConverter extends Converter<Set<String>> {
    @Nullable
    @Override
    public Set<String> fromString(@NotNull String value) {
      final THashSet<String> result = new THashSet<>();
      for (String closingTag : StringUtil.split(value, ",")) {
        result.add(StringUtil.toLowerCase(closingTag.trim()));
      }
      return result;
    }

    @NotNull
    @Override
    public String toString(@NotNull Set<String> o) {
      return StringUtil.join(o, ", ");
    }
  }
}
