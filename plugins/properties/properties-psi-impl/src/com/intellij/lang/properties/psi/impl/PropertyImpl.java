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
package com.intellij.lang.properties.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.lang.properties.PropertyManipulator;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyStub;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class PropertyImpl extends PropertiesStubElementImpl<PropertyStub> implements Property, PsiLanguageInjectionHost {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.properties.psi.impl.PropertyImpl");

  public PropertyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public PropertyImpl(final PropertyStub stub, final IStubElementType nodeType) {
    super(stub, nodeType);
  }

  public String toString() {
    return "Property{ key = " + getKey() + ", value = " + getValue() + "}";
  }

  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), name, "xxx", null);
    ASTNode keyNode = getKeyNode();
    ASTNode newKeyNode = property.getKeyNode();
    LOG.assertTrue(newKeyNode != null);
    if (keyNode == null) {
      getNode().addChild(newKeyNode);
    }
    else {
      getNode().replaceChild(keyNode, newKeyNode);
    }
    return this;
  }

  public void setValue(@NotNull String value) throws IncorrectOperationException {
    ASTNode node = getValueNode();
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), "xxx", value, getKeyValueDelimiter());
    ASTNode valueNode = property.getValueNode();
    if (node == null) {
      if (valueNode != null) {
        getNode().addChild(valueNode);
      }
    }
    else {
      if (valueNode == null) {
        getNode().removeChild(node);
      }
      else {
        getNode().replaceChild(node, valueNode);
      }
    }
  }

  public String getName() {
    return getUnescapedKey();
  }

  public String getKey() {
    final PropertyStub stub = getStub();
    if (stub != null) {
      return stub.getKey();
    }

    final ASTNode node = getKeyNode();
    if (node == null) {
      return null;
    }
    return node.getText();
  }

  @Nullable
  public ASTNode getKeyNode() {
    return getNode().findChildByType(PropertiesTokenTypes.KEY_CHARACTERS);
  }

  @Nullable
  public ASTNode getValueNode() {
    return getNode().findChildByType(PropertiesTokenTypes.VALUE_CHARACTERS);
  }

  public String getValue() {
    final ASTNode node = getValueNode();
    if (node == null) {
      return "";
    }
    return node.getText();
  }

  @Nullable
  public String getUnescapedValue() {
    return unescape(getValue());
  }


  public static String unescape(String s) {
    if (s == null) return null;
    StringBuilder sb = new StringBuilder();
    parseCharacters(s, sb, null);
    return sb.toString();
  }

  public static boolean parseCharacters(String s, StringBuilder outChars, @Nullable int[] sourceOffsets) {
    assert sourceOffsets == null || sourceOffsets.length == s.length() + 1;
    int off = 0;
    int len = s.length();

    boolean result = true;
    final int outOffset = outChars.length();
    while (off < len) {
      char aChar = s.charAt(off++);
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length() - outOffset] = off - 1;
        sourceOffsets[outChars.length() + 1 - outOffset] = off;
      }

      if (aChar == '\\') {
        aChar = s.charAt(off++);
        if (aChar == 'u') {
          // Read the xxxx
          int value = 0;
          boolean error = false;
          for (int i = 0; i < 4 && off < s.length(); i++) {
            aChar = s.charAt(off++);
            switch (aChar) {
              case '0':
              case '1':
              case '2':
              case '3':
              case '4':
              case '5':
              case '6':
              case '7':
              case '8':
              case '9':
                value = (value << 4) + aChar - '0';
                break;
              case 'a':
              case 'b':
              case 'c':
              case 'd':
              case 'e':
              case 'f':
                value = (value << 4) + 10 + aChar - 'a';
                break;
              case 'A':
              case 'B':
              case 'C':
              case 'D':
              case 'E':
              case 'F':
                value = (value << 4) + 10 + aChar - 'A';
                break;
              default:
                outChars.append("\\u");
                int start = off - i - 1;
                int end = start + 4 < s.length() ? start + 4 : s.length();
                outChars.append(s, start, end);
                i=4;
                error = true;
                off = end;
                break;
                //throw new IllegalArgumentException("Malformed \\uxxxx encoding.");
            }
          }
          if (!error) {
            outChars.append((char)value);
          }
          else {
            result = false;
          }
        }
        else if (aChar == '\n') {
          // escaped linebreak: skip whitespace in the beginning of next line
          while (off < len && (s.charAt(off) == ' ' || s.charAt(off) == '\t')) {
            off++;
          }
        }
        else if (aChar == 't') {
          outChars.append('\t');
        }
        else if (aChar == 'r') {
          outChars.append('\r');
        }
        else if (aChar == 'n') {
          outChars.append('\n');
        }
        else if (aChar == 'f') {
          outChars.append('\f');
        }
        else {
          outChars.append(aChar);
        }
      }
      else {
        outChars.append(aChar);
      }
      if (sourceOffsets != null) {
        sourceOffsets[outChars.length() - outOffset] = off;
      }
    }
    return result;
  }

  @Nullable
  public static TextRange trailingSpaces(String s) {
    if (s == null) {
      return null;
    }
    int off = 0;
    int len = s.length();
    int startSpaces = -1;

    while (off < len) {
      char aChar = s.charAt(off++);
      if (aChar == '\\') {
        if (startSpaces == -1) startSpaces = off-1;
        aChar = s.charAt(off++);
        if (aChar == 'u') {
          // Read the xxxx
          int value = 0;
          boolean error = false;
          for (int i = 0; i < 4; i++) {
            aChar = off < s.length() ? s.charAt(off++) : 0;
            switch (aChar) {
              case '0':
              case '1':
              case '2':
              case '3':
              case '4':
              case '5':
              case '6':
              case '7':
              case '8':
              case '9':
                value = (value << 4) + aChar - '0';
                break;
              case 'a':
              case 'b':
              case 'c':
              case 'd':
              case 'e':
              case 'f':
                value = (value << 4) + 10 + aChar - 'a';
                break;
              case 'A':
              case 'B':
              case 'C':
              case 'D':
              case 'E':
              case 'F':
                value = (value << 4) + 10 + aChar - 'A';
                break;
              default:
                int start = off - i - 1;
                int end = start + 4 < s.length() ? start + 4 : s.length();
                i=4;
                error = true;
                off = end;
                startSpaces = -1;
                break;
            }
          }
          if (!error) {
            if (Character.isWhitespace(value)) {
              if (startSpaces == -1) {
                startSpaces = off-1;
              }
            }
            else {
              startSpaces = -1;
            }
          }
        }
        else if (aChar == '\n') {
          // escaped linebreak: skip whitespace in the beginning of next line
          while (off < len && (s.charAt(off) == ' ' || s.charAt(off) == '\t')) {
            off++;
          }
        }
        else if (aChar == 't') {
          if (startSpaces == -1) startSpaces = off;
        }
        else if (aChar == 'r') {
          if (startSpaces == -1) startSpaces = off;
        }
        else if (aChar == 'n') {
          if (startSpaces == -1) startSpaces = off;
        }
        else if (aChar == 'f') {
          if (startSpaces == -1) startSpaces = off;
        }
        else {
          if (Character.isWhitespace(aChar)) {
            if (startSpaces == -1) {
              startSpaces = off-1;
            }
          }
          else {
            startSpaces = -1;
          }
        }
      }
      else {
        if (Character.isWhitespace(aChar)) {
          if (startSpaces == -1) {
            startSpaces = off-1;
          }
        }
        else {
          startSpaces = -1;
        }
      }
    }
    return startSpaces == -1 ? null : new TextRange(startSpaces, len);
  }

  @Nullable
  public String getUnescapedKey() {
    return unescape(getKey());
  }

  public Icon getIcon(int flags) {
    return PlatformIcons.PROPERTY_ICON;
  }

  public void delete() throws IncorrectOperationException {
    final ASTNode parentNode = getParent().getNode();
    assert parentNode != null;

    ASTNode node = getNode();
    ASTNode prev = node.getTreePrev();
    ASTNode next = node.getTreeNext();
    parentNode.removeChild(node);
    if ((prev == null || prev.getElementType() == TokenType.WHITE_SPACE) && next != null &&
        next.getElementType() == TokenType.WHITE_SPACE) {
      parentNode.removeChild(next);
    }
  }

  public PropertiesFile getPropertiesFile() {
    return (PropertiesFile)super.getContainingFile();
  }

  public String getDocCommentText() {
    StringBuilder text = new StringBuilder();
    for (PsiElement doc = getPrevSibling(); doc != null; doc = doc.getPrevSibling()) {
      if (doc instanceof PsiWhiteSpace) {
        doc = doc.getPrevSibling();
      }
      if (doc instanceof PsiComment) {
        if (text.length() != 0) text.insert(0, "\n");
        String comment = doc.getText();
        text.insert(0, comment);
      }
      else {
        break;
      }
    }
    if (text.length() == 0) return null;
    return text.toString();
  }

  @NotNull
  @Override
  public PsiElement getPsiElement() {
    return this;
  }

  @NotNull
  public SearchScope getUseScope() {
    // property ref can occur in any file
    return GlobalSearchScope.allScope(getProject());
  }

  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      public String getPresentableText() {
        return getName();
      }

      public String getLocationString() {
        return getPropertiesFile().getName();
      }

      public Icon getIcon(final boolean open) {
        return null;
      }
    };
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return new PropertyManipulator().handleContentChange(this, text);
  }

  @NotNull
  @Override
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new PropertyImplEscaper(this);
  }

  public char getKeyValueDelimiter() {
    final PsiElement delimiter = findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    if (delimiter == null) {
      return ' ';
    }
    final String text = delimiter.getText();
    LOG.assertTrue(text.length() == 1);
    return text.charAt(0);
  }

  public void replaceKeyValueDelimiterWithDefault() {
    PropertyImpl property = (PropertyImpl)PropertiesElementFactory.createProperty(getProject(), "yyy", "xxx", null);
    final ASTNode oldDelimiter = getNode().findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    LOG.assertTrue(oldDelimiter != null);
    final ASTNode newDelimiter = property.getNode().findChildByType(PropertiesTokenTypes.KEY_VALUE_SEPARATOR);
    LOG.assertTrue(newDelimiter != null);
    getNode().replaceChild(oldDelimiter, newDelimiter);
  }
}
