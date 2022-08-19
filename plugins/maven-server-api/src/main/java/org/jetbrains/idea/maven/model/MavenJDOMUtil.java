// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.model;

import com.intellij.openapi.util.text.StringUtilRt;
import org.jdom.Attribute;
import org.jdom.Content;
import org.jdom.Element;
import org.jdom.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Methods in this class are copied from {@link com.intellij.openapi.util.JDOMUtil} to avoid dependency on 'intellij.platform.util' module
 * in Maven server classes.
 */
final class MavenJDOMUtil {
  static boolean areElementsEqual(@Nullable Element e1, @Nullable Element e2) {
    if (e1 == null && e2 == null) return true;
    if (e1 == null || e2 == null) return false;

    return Objects.equals(e1.getName(), e2.getName())
           && isAttributesEqual(e1.getAttributes(), e2.getAttributes())
           && contentListsEqual(e1.content().filter(CONTENT_FILTER), e2.content().filter(CONTENT_FILTER));
  }

  private static boolean contentListsEqual(Stream<Content> c1, Stream<Content> c2) {
    if (c1 == null && c2 == null) return true;
    if (c1 == null || c2 == null) return false;

    Iterator<Content> l1 = c1.iterator();
    Iterator<Content> l2 = c2.iterator();
    while (l1.hasNext() && l2.hasNext()) {
      if (!contentsEqual(l1.next(), l2.next())) {
        return false;
      }
    }

    return l1.hasNext() == l2.hasNext();
  }

  private static boolean contentsEqual(Content c1, Content c2) {
    if (!(c1 instanceof Element) && !(c2 instanceof Element)) {
      return c1.getValue().equals(c2.getValue());
    }

    return c1 instanceof Element && c2 instanceof Element && areElementsEqual((Element)c1, (Element)c2);
  }

  private static boolean isAttributesEqual(@NotNull List<Attribute> l1, @NotNull List<Attribute> l2) {
    if (l1.size() != l2.size()) return false;
    for (int i = 0; i < l1.size(); i++) {
      if (!attEqual(l1.get(i), l2.get(i))) return false;
    }
    return true;
  }

  private static boolean attEqual(@NotNull Attribute a1, @NotNull Attribute a2) {
    return a1.getName().equals(a2.getName()) && a1.getValue().equals(a2.getValue());
  }

  private static final Predicate<Content> CONTENT_FILTER = content -> {
    return !(content instanceof Text) || !StringUtilRt.isEmptyOrSpaces(((Text)content).getText());
  };

  static int getTreeHash(@NotNull Element root) {
    return addToHash(0, root);
  }

  private static int addToHash(int i, @NotNull Element element) {
    i = addToHash(i, element.getName());

    for (Attribute attribute : element.getAttributes()) {
      i = addToHash(i, attribute.getName());
      i = addToHash(i, attribute.getValue());
    }

    for (Content child : element.getContent()) {
      if (child instanceof Element) {
        i = addToHash(i, (Element)child);
      }
      else if (child instanceof Text) {
        String text = ((Text)child).getText();
        if (!StringUtilRt.isEmptyOrSpaces(text)) {
          i = addToHash(i, text);
        }
      }
    }
    return i;
  }

  private static int addToHash(int i, @NotNull String s) {
    return i * 31 + s.hashCode();
  }
}
