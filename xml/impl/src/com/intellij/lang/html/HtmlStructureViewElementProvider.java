package com.intellij.lang.html;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.xml.XmlTagTreeElement;
import com.intellij.ide.structureView.xml.XmlStructureViewElementProvider;
import com.intellij.navigation.LocationPresentation;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class HtmlStructureViewElementProvider implements XmlStructureViewElementProvider {

  private static int MAX_TEXT_LENGTH = 50;

  @Nullable
  public StructureViewTreeElement createCustomXmlTagTreeElement(@NotNull final XmlTag tag) {
    if (tag.getContainingFile().getViewProvider().getVirtualFile().getFileType() != HtmlFileType.INSTANCE) return null;

    return new HtmlTagTreeElement(tag);
  }

  static class HtmlTagTreeElement extends XmlTagTreeElement implements LocationPresentation {
    public HtmlTagTreeElement(final XmlTag tag) {
      super(tag);
    }

    public String getPresentableText() {
      final XmlTag tag = getElement();
      if (tag == null) return IdeBundle.message("node.structureview.invalid");

      final String id = toCanonicalForm(tag.getAttributeValue("id"));

      final String classValue = tag.getAttributeValue("class");
      final List<String> classValues = classValue != null ? StringUtil.split(classValue, " ") : Collections.<String>emptyList();

      final StringBuilder text = new StringBuilder(tag.getLocalName());

      if (id != null) {
        text.append("#").append(id);
      }

      if (!classValues.isEmpty()) {
        text.append('.').append(StringUtil.join(classValues, "."));
      }

      return text.toString();
    }

    public String getLocationString() {
      final XmlTag tag = getElement();
      if (tag == null) return null;

      final String text = normalizeSpaces(tag.getValue().getTrimmedText());
      return text.isEmpty() ? null : shortenTextIfLong(text);
    }

    private static String normalizeSpaces(final String text) {
      final StringBuilder buf = new StringBuilder();

      for (char ch : text.toCharArray()) {
        if (ch <= ' ' || Character.isSpaceChar(ch)) {
          if (buf.length() == 0 || buf.charAt(buf.length() - 1) != ' ') {
            buf.append(' ');
          }
        }
        else {
          buf.append(ch);
        }
      }

      return buf.toString();
    }

    private static String shortenTextIfLong(final String text) {
      if (text.length() <= MAX_TEXT_LENGTH) return text;

      int index;
      for (index = MAX_TEXT_LENGTH; index > MAX_TEXT_LENGTH - 20; index--) {
        if (!Character.isLetter(text.charAt(index))) {
          break;
        }
      }

      final int endIndex = Character.isLetter(index) ? MAX_TEXT_LENGTH : index;
      return text.substring(0, endIndex) + "...";
    }

    public String getLocationPrefix() {
      return "  ";
    }

    public String getLocationSuffix() {
      return "";
    }
  }
}
