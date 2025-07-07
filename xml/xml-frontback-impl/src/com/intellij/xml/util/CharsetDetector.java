// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.util;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.psi.impl.source.parsing.xml.HtmlBuilderDriver;
import com.intellij.psi.impl.source.parsing.xml.XmlBuilder;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

@ApiStatus.Internal
public final class CharsetDetector {
  private CharsetDetector() { }

  private static final @NonNls String CHARSET = "charset";
  private static final @NonNls String CHARSET_PREFIX = CHARSET + "=";

  private static class TerminateException extends RuntimeException {
    private static final TerminateException INSTANCE = new TerminateException();
  }

  public static Charset detectCharsetFromMetaTag(@NotNull CharSequence content) {
    // check for <meta http-equiv="charset=CharsetName" > or <meta charset="CharsetName"> and return Charset
    // because we will lightly parse and explicit charset isn't used very often do quick check for applicability
    int charPrefix = StringUtil.indexOf(content, CHARSET);
    do {
      if (charPrefix == -1) return null;
      int charsetPrefixEnd = charPrefix + CHARSET.length();
      while (charsetPrefixEnd < content.length() && Character.isWhitespace(content.charAt(charsetPrefixEnd))) ++charsetPrefixEnd;
      if (charsetPrefixEnd < content.length() && content.charAt(charsetPrefixEnd) == '=') break;
      charPrefix = StringUtil.indexOf(content, CHARSET, charsetPrefixEnd);
    }
    while (true);

    if (content.length() > charPrefix + 200) {
      String name = tryFetchCharsetFromFileContent(content.subSequence(0, charPrefix + 200));
      if (name != null) {
        return CharsetToolkit.forName(name);
      }
    }
    String name = tryFetchCharsetFromFileContent(content);
    return CharsetToolkit.forName(name);
  }

  private static String tryFetchCharsetFromFileContent(@NotNull CharSequence content) {
    Ref<String> charsetNameRef = new Ref<>();
    try {
      new HtmlBuilderDriver(content).build(new XmlBuilder() {
        final @NonNls Set<String> inTag = new HashSet<>();
        boolean metHttpEquiv;
        boolean metHtml5Charset;

        @Override
        public void doctype(@Nullable CharSequence publicId,
                            @Nullable CharSequence systemId,
                            int startOffset,
                            int endOffset) {
        }

        @Override
        public @NotNull ProcessingOrder startTag(@NotNull CharSequence localName,
                                                 @NotNull String namespace,
                                                 int startOffset,
                                                 int endOffset,
                                                 int headerEndOffset) {
          @NonNls String name = StringUtil.toLowerCase(localName.toString());
          inTag.add(name);
          if (!inTag.contains("head") && !"html".equals(name)) terminate();
          return ProcessingOrder.TAGS_AND_ATTRIBUTES;
        }

        private static void terminate() {
          throw TerminateException.INSTANCE;
        }

        @Override
        public void endTag(@NotNull CharSequence localName, @NotNull String namespace, int startOffset, int endOffset) {
          @NonNls String name = StringUtil.toLowerCase(localName.toString());
          if ("meta".equals(name) && (metHttpEquiv || metHtml5Charset) && contentAttributeValue != null) {
            String charsetName;
            if (metHttpEquiv) {
              int start = contentAttributeValue.indexOf(CHARSET_PREFIX);
              if (start == -1) return;
              start += CHARSET_PREFIX.length();
              int end = contentAttributeValue.indexOf(';', start);
              if (end == -1) end = contentAttributeValue.length();
              charsetName = contentAttributeValue.substring(start, end);
            }
            else /*if (metHtml5Charset) */ {
              charsetName = StringUtil.unquoteString(contentAttributeValue);
            }
            charsetNameRef.set(charsetName);
            terminate();
          }
          if ("head".equals(name)) {
            terminate();
          }
          inTag.remove(name);
          metHttpEquiv = false;
          metHtml5Charset = false;
          contentAttributeValue = null;
        }

        private String contentAttributeValue;

        @Override
        public void attribute(@NotNull CharSequence localName, @NotNull CharSequence v, int startOffset, int endOffset) {
          @NonNls String name = StringUtil.toLowerCase(localName.toString());
          if (inTag.contains("meta")) {
            @NonNls String value = StringUtil.toLowerCase(v.toString());
            if (name.equals("http-equiv")) {
              metHttpEquiv |= value.equals("content-type");
            }
            else if (name.equals(CHARSET)) {
              metHtml5Charset = true;
              contentAttributeValue = value;
            }
            if (name.equals("content")) {
              contentAttributeValue = value;
            }
          }
        }

        @Override
        public void textElement(@NotNull CharSequence display, @NotNull CharSequence physical, int startOffset, int endOffset) {
        }

        @Override
        public void entityRef(@NotNull CharSequence ref, int startOffset, int endOffset) {
        }

        @Override
        public void error(@NotNull String message, int startOffset, int endOffset) {
        }
      });
    }
    catch (TerminateException ignored) {
      //ignore
    }
    catch (Exception ignored) {
      // some weird things can happen, like an unbalanced tree
    }

    return charsetNameRef.get();
  }
}
