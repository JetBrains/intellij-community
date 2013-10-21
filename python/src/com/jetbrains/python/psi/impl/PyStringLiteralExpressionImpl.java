/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.psi.impl;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.regexp.PythonVerboseRegexpLanguage;
import com.jetbrains.python.lexer.PyStringLiteralLexer;
import com.jetbrains.python.lexer.PythonHighlightingLexer;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression, RegExpLanguageHost {
  public static final Pattern PATTERN_ESCAPE = Pattern
      .compile("\\\\(\n|\\\\|'|\"|a|b|f|n|r|t|v|([0-7]{1,3})|x([0-9a-fA-F]{1,2})" + "|N(\\{.*?\\})|u([0-9a-fA-F]{4})|U([0-9a-fA-F]{8}))");
         //        -> 1                        ->   2      <-->     3          <-     ->   4     <-->    5      <-   ->  6           <-<-
  private static final Map<String, String> escapeMap = initializeEscapeMap();
  private String stringValue;
  private List<TextRange> valueTextRanges;
  private final DefaultRegExpPropertiesProvider myPropertiesProvider;

  private static Map<String, String> initializeEscapeMap() {
    Map<String, String> map = new HashMap<String, String>();
    map.put("\n", "\n");
    map.put("\\", "\\");
    map.put("'", "'");
    map.put("\"", "\"");
    map.put("a", "\001");
    map.put("b", "\b");
    map.put("f", "\f");
    map.put("n", "\n");
    map.put("r", "\r");
    map.put("t", "\t");
    map.put("v", "\013");
    return map;
  }

  public PyStringLiteralExpressionImpl(ASTNode astNode) {
    super(astNode);
    myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyStringLiteralExpression(this);
  }

  public void subtreeChanged() {
    super.subtreeChanged();
    stringValue = null;
    valueTextRanges = null;
  }

  public List<TextRange> getStringValueTextRanges() {
    if (valueTextRanges == null) {
      int elStart = getTextRange().getStartOffset();
      List<TextRange> ranges = new ArrayList<TextRange>();
      for (ASTNode node : getStringNodes()) {
        TextRange range = getNodeTextRange(node.getText());
        int nodeOffset = node.getStartOffset() - elStart;
        ranges.add(TextRange.from(nodeOffset + range.getStartOffset(), range.getLength()));
      }
      valueTextRanges = Collections.unmodifiableList(ranges);
    }
    return valueTextRanges;
  }

  public static TextRange getNodeTextRange(final String text) {
    int startOffset = getPrefixLength(text);
    int delimiterLength = 1;
    final String afterPrefix = text.substring(startOffset);
    if (afterPrefix.startsWith("\"\"\"") || afterPrefix.startsWith("'''")) {
      delimiterLength = 3;
    }
    final String delimiter = text.substring(startOffset, startOffset + delimiterLength);
    startOffset += delimiterLength;
    int endOffset = text.length();
    if (text.substring(startOffset).endsWith(delimiter)) {
      endOffset -= delimiterLength;
    }
    return new TextRange(startOffset, endOffset);
  }

  public static int getPrefixLength(String text) {
    int startOffset = 0;
    startOffset = PyStringLiteralLexer.skipEncodingPrefix(text, startOffset);
    startOffset = PyStringLiteralLexer.skipRawPrefix(text, startOffset);
    startOffset = PyStringLiteralLexer.skipEncodingPrefix(text, startOffset);
    startOffset = PyStringLiteralLexer.skipRawPrefix(text, startOffset);
    return startOffset;
  }

  private static boolean isRaw(String text) {
    int startOffset = PyStringLiteralLexer.skipEncodingPrefix(text, 0);
    return PyStringLiteralLexer.skipRawPrefix(text, startOffset) > startOffset;
  }

  private static boolean isUnicode(String text) {
    return text.length() > 0 && Character.toUpperCase(text.charAt(0)) == 'U';                       //TODO[ktisha]
  } 

  private static boolean isBytes(String text) {
    return text.length() > 0 && Character.toUpperCase(text.charAt(0)) == 'B';
  }

  private static boolean isChar(String text) {
    return text.length() > 0 && Character.toUpperCase(text.charAt(0)) == 'C';
  }

  public void iterateCharacterRanges(TextRangeConsumer consumer) {
    int elStart = getTextRange().getStartOffset();
    for (ASTNode child : getStringNodes()) {
      final String text = child.getText();
      TextRange textRange = getNodeTextRange(text);
      int offset = child.getTextRange().getStartOffset() - elStart + textRange.getStartOffset();
      String undecoded = textRange.substring(text);
      if (!iterateCharacterRanges(consumer, undecoded, offset, isRaw(text), isUnicode(text))) {
        break;
      }
    }
  }



  public List<ASTNode> getStringNodes() {
    return Arrays.asList(getNode().getChildren(PyTokenTypes.STRING_NODES));
  }

  public String getStringValue() {
    //ASTNode child = getNode().getFirstChildNode();
    //assert child != null;
   if (stringValue == null) {
      final StringBuilder out = new StringBuilder();
      iterateCharacterRanges(new TextRangeConsumer() {
        public boolean process(int startOffset, int endOffset, String value) {
          out.append(value);
          return true;
        }
      });
      stringValue = out.toString();
    }
    return stringValue;
  }

  @Override
  public TextRange getStringValueTextRange() {
    List<TextRange> allRanges = getStringValueTextRanges();
    if (allRanges.size() == 1) {
      return allRanges.get(0);
    }
    if (allRanges.size() > 1) {
      return new TextRange(allRanges.get(0).getStartOffset(), allRanges.get(allRanges.size()-1).getEndOffset());
    }
    return new TextRange(0, getTextLength());
  }

  private static boolean iterateCharacterRanges(TextRangeConsumer consumer, String undecoded, int off, boolean raw, boolean unicode) {
    if (raw) {
      return iterateRawCharacterRanges(consumer, undecoded, off, unicode);
    }
    Matcher escMatcher = PATTERN_ESCAPE.matcher(undecoded);
    int index = 0;
    while (escMatcher.find(index)) {
      for (int i = index; i < escMatcher.start(); i++) {
        if (!consumer.process(off + i, off + i + 1, Character.toString(undecoded.charAt(i)))) {
          return false;
        }
      }
      String octal = escMatcher.group(2);
      String hex = escMatcher.group(3);
      String str = null;
      if (octal != null) {
        str = new String(new char[]{(char)Integer.parseInt(octal, 8)});

      }
      else if (hex != null) {
        str = new String(new char[]{(char)Integer.parseInt(hex, 16)});

      }
      else {
        String toReplace = escMatcher.group(1);
        String replacement = escapeMap.get(toReplace);
        if (replacement != null) {
          str = replacement;
        }
      }
      String unicodeName = escMatcher.group(4);
      String unicode32 = escMatcher.group(6);

      if (unicode32 != null) {
        str = unicode ? new String(Character.toChars((int)Long.parseLong(unicode32, 16))) : unicode32;
      }
      if (unicodeName != null) {
        //TOLATER: implement unicode character name escapes
      }
      String unicode16 = escMatcher.group(5);
      if (unicode16 != null) {
        str = unicode ? new String(new char[]{(char)Integer.parseInt(unicode16, 16)}) : unicode16;
      }

      if (str != null) {
        int start = escMatcher.start();
        int end = escMatcher.end();
        if (!consumer.process(off + start, off + end, str)) {
          return false;
        }
      }
      index = escMatcher.end();
    }
    for (int i = index; i < undecoded.length(); i++) {
      if (!consumer.process(off + i, off + i + 1, Character.toString(undecoded.charAt(i)))) {
        return false;
      }
    }
    return true;
  }

  private static boolean iterateRawCharacterRanges(TextRangeConsumer consumer, String undecoded, int off, boolean unicode) {
    for (int i = 0; i < undecoded.length(); i++) {
      char c = undecoded.charAt(i);
      if (unicode && c == '\\' && i < undecoded.length()-1) {
        char c2 = undecoded.charAt(i+1);
        if (c2 == 'u' && i < undecoded.length()-5) {
          try {
            char u = (char) Integer.parseInt(undecoded.substring(i+2, i+6), 16);
            if (!consumer.process(off+i, off+i+ 6, Character.toString(u))) {
              return false;
            }
          }
          catch (NumberFormatException ignore) { }
          //noinspection AssignmentToForLoopParameter
          i += 5;
          continue;
        }
        if (c2 == 'U' && i < undecoded.length()-9) {
          // note: Java has 16-bit chars, so this code will truncate characters which don't fit in 16 bits
          try {
            char u = (char) Long.parseLong(undecoded.substring(i+2, i+10), 16);
            if (!consumer.process(off+i, off+i+10, Character.toString(u))) {
              return false;
            }
          }
          catch (NumberFormatException ignore) { }
          //noinspection AssignmentToForLoopParameter
          i += 9;
          continue;
        }
      }
      if (!consumer.process(off + i, off + i + 1, Character.toString(c))) {
        return false;
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getStringValue();
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  public PyType getType(@NotNull TypeEvalContext context, @NotNull TypeEvalContext.Key key) {
    final List<ASTNode> nodes = getStringNodes();
    if (nodes.size() > 0) {
      String text = getStringNodes().get(0).getText();

      PyFile file = PsiTreeUtil.getParentOfType(this, PyFile.class);
      if (file != null) {
        IElementType type = PythonHighlightingLexer.convertStringType(getStringNodes().get(0).getElementType(), text,
                                                LanguageLevel.forElement(this), file.hasImportFromFuture(FutureFeature.UNICODE_LITERALS));
        if (PyTokenTypes.UNICODE_NODES.contains(type)) {
          return PyBuiltinCache.getInstance(this).getUnicodeType(LanguageLevel.forElement(this));
        }
      }
    }
    return PyBuiltinCache.getInstance(this).getBytesType(LanguageLevel.forElement(this));
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  @Override
  public ItemPresentation getPresentation() {
    return new ItemPresentation() {
      @Nullable
      @Override
      public String getPresentableText() {
        return getStringValue();
      }

      @Nullable
      @Override
      public String getLocationString() {
        return "(" + PyPresentableElementImpl.getPackageForFile(getContainingFile()) + ")";
      }

      @Nullable
      @Override
      public Icon getIcon(boolean unused) {
        return AllIcons.Nodes.Variable;
      }
    };
  }

  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    // TODO is this the correct implementation? most likely not
    ASTNode valueNode = getNode().getFirstChildNode();
    assert valueNode instanceof LeafElement;
    ((LeafElement)valueNode).replaceWithText(text);
    return this;
  }

  @NotNull
  public LiteralTextEscaper<? extends PsiLanguageInjectionHost> createLiteralTextEscaper() {
    return new StringLiteralTextEscaper(this);
  }

  private static class StringLiteralTextEscaper extends LiteralTextEscaper<PyStringLiteralExpression> {
    private final PyStringLiteralExpressionImpl myHost;

    protected StringLiteralTextEscaper(@NotNull PyStringLiteralExpressionImpl host) {
      super(host);
      myHost = host;
    }

    @Override
    public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull final StringBuilder outChars) {
      final PyDocStringOwner
        docStringOwner = PsiTreeUtil.getParentOfType(myHost, PyDocStringOwner.class);
      if (docStringOwner != null && myHost.equals(docStringOwner.getDocStringExpression())) {
        outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
      }
      else {
        myHost.iterateCharacterRanges(new TextRangeConsumer() {
          public boolean process(int startOffset, int endOffset, String value) {
            int xsectStart = Math.max(startOffset, rangeInsideHost.getStartOffset());
            int xsectEnd = Math.min(endOffset, rangeInsideHost.getEndOffset());
            if (xsectEnd > xsectStart) {
              outChars.append(value);
            }
            return endOffset < rangeInsideHost.getEndOffset();
          }
        });
      }
      return true;
    }

    @Override
    public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
      final Ref<Integer> resultRef = Ref.create(-1);
      final Ref<Integer> indexRef = Ref.create(0);
      final Ref<Integer> lastEndOffsetRef = Ref.create(-1);
      myHost.iterateCharacterRanges(new TextRangeConsumer() {
        @Override
        public boolean process(int startOffset, int endOffset, String value) {
          if (startOffset > rangeInsideHost.getEndOffset()) {
            return false;
          }
          lastEndOffsetRef.set(endOffset);
          if (startOffset >= rangeInsideHost.getStartOffset()) {
            final int i = indexRef.get();
            if (i == offsetInDecoded) {
              resultRef.set(startOffset);
              return false;
            }
            indexRef.set(i + 1);
          }
          return true;
        }
      });
      final int result = resultRef.get();
      if (result != -1) {
        return result;
      }
      // We should handle the position of a character at the end of rangeInsideHost, because LeafPatcher expects it to be valid
      final int lastEndOffset = lastEndOffsetRef.get();
      if (indexRef.get() == offsetInDecoded && lastEndOffset == rangeInsideHost.getEndOffset()) {
        return lastEndOffset;
      }
      return -1;
    }

    @Override
    public boolean isOneLine() {
      return false;
    }
  }

  @Override
  public int valueOffsetToTextOffset(int valueOffset) {
    final Ref<Integer> offsetInDecodedRef = new Ref<Integer>(valueOffset);
    final Ref<Integer> result = new Ref<Integer>(-1);
    iterateCharacterRanges(new TextRangeConsumer() {
      public boolean process(int startOffset, int endOffset, String value) {
        if (value.length() > offsetInDecodedRef.get()) {
          result.set(startOffset + offsetInDecodedRef.get());
          return false;
        }
        offsetInDecodedRef.set(offsetInDecodedRef.get() - value.length());
        if (offsetInDecodedRef.get() == 0) {
          result.set(endOffset);
          return false;
        }
        return true;
      }
    });
    return result.get();
  }

  public boolean characterNeedsEscaping(char c) {
    if (c == '#') {
      return isVerboseInjection();
    }
    return c == ']' || c == '}' || c == '\"' || c == '\'';
  }

  private boolean isVerboseInjection() {
    List<Pair<PsiElement,TextRange>> files = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(this);
    if (files != null) {
      for (Pair<PsiElement, TextRange> file : files) {
        Language language = file.getFirst().getLanguage();
        if (language == PythonVerboseRegexpLanguage.INSTANCE) {
          return true;
        }
      }
    }
    return false;
  }

  public boolean supportsPerl5EmbeddedComments() {
    return true;
  }

  public boolean supportsPossessiveQuantifiers() {
    return false;
  }

  public boolean supportsPythonConditionalRefs() {
    return true;
  }

  public boolean supportsNamedGroupSyntax(RegExpGroup group) {
    return group.isPythonNamedGroup();
  }

  @Override
  public boolean isValidCategory(@NotNull String category) {
    return myPropertiesProvider.isValidCategory(category);
  }

  @NotNull
  @Override
  public String[][] getAllKnownProperties() {
    return myPropertiesProvider.getAllKnownProperties();
  }

  @Nullable
  @Override
  public String getPropertyDescription(@Nullable String name) {
    return myPropertiesProvider.getPropertyDescription(name);
  }

  @NotNull
  @Override
  public String[][] getKnownCharacterClasses() {
    return myPropertiesProvider.getKnownCharacterClasses();
  }
}
