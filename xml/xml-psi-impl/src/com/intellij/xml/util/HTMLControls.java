// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.io.UnsyncByteArrayInputStream;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public final class HTMLControls {
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
    return deserialize(element);
  }

  private static Control[] deserialize(Element element) {
    ArrayList<Control> controls = new ArrayList<>();
    for (Element child : element.getChildren()) {
      if ("control".equals(child.getName())) {
        Control control = new Control(
          child.getAttributeValue("name"),
          TagState.valueOf(StringUtil.toUpperCase(child.getAttributeValue("startTag"))),
          TagState.valueOf(StringUtil.toUpperCase(child.getAttributeValue("endTag"))),
          "true".equalsIgnoreCase(child.getAttributeValue("emptyAllowed")),
          autoClosed(child.getAttributeValue("autoClosedBy")));
        controls.add(control);
      }
    }
    return controls.toArray(new Control[0]);
  }

  private static Set<String> autoClosed(@Nullable String value) {
    if (value == null) return Collections.emptySet();
    Set<String> result = new HashSet<>();
    for (String closingTag : StringUtil.split(value, ",")) {
      result.add(StringUtil.toLowerCase(closingTag.trim()));
    }
    return result;
  }

  public enum TagState {REQUIRED, OPTIONAL, FORBIDDEN}

  public static class Control {
    public final String name;
    public final TagState startTag;
    public final TagState endTag;
    public final boolean emptyAllowed;
    public final Set<String> autoClosedBy;

    public Control(String name, TagState startTag, TagState endTag, boolean emptyAllowed, Set<String> autoClosedBy) {
      this.name = name;
      this.endTag = endTag;
      this.startTag = startTag;
      this.emptyAllowed = emptyAllowed;
      this.autoClosedBy = autoClosedBy;
    }
  }
}
