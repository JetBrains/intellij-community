package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression, RegExpLanguageHost {
  private static final Pattern PATTERN_ESCAPE = Pattern
      .compile("\\\\(\n|\\\\|'|\"|a|b|f|n|r|t|v|([0-7]{1,3})|x([0-9a-fA-F]{1,2})" + "|N(\\{.*?\\})|u([0-9a-fA-F]){4}|U([0-9a-fA-F]{8}))");
  private static final Map<String, String> escapeMap = initializeEscapeMap();
  private String stringValue;
  private List<TextRange> valueTextRanges;

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

  private static TextRange getNodeTextRange(final String text) {
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
    startOffset = skipEncodingPrefix(text, startOffset);
    startOffset = skipRawPrefix(text, startOffset);
    return startOffset;
  }

  private static int skipRawPrefix(String text, int startOffset) {
    char c = Character.toUpperCase(text.charAt(startOffset));
    if (c == 'R') {
      startOffset++;
    }
    return startOffset;
  }

  private static int skipEncodingPrefix(String text, int startOffset) {
    char c = Character.toUpperCase(text.charAt(startOffset));
    if (c == 'U' || c == 'B') {
      startOffset++;
    }
    return startOffset;
  }

  private static boolean isRaw(String text) {
    int startOffset = skipEncodingPrefix(text, 0);
    return skipRawPrefix(text, startOffset) > startOffset;
  }

  private static boolean isUnicode(String text) {
    return text.length() > 0 && Character.toUpperCase(text.charAt(0)) == 'U';
  } 

  private static boolean isBytes(String text) {
    return text.length() > 0 && Character.toUpperCase(text.charAt(0)) == 'B';
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
    return Arrays.asList(getNode().getChildren(TokenSet.create(PyTokenTypes.STRING_LITERAL)));
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
      if (!raw) {
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
      }
      if (unicode) {
        if (!raw) {
          String unicodeName = escMatcher.group(4);
          String unicode32 = escMatcher.group(6);

          if (unicode32 != null) {
            str = new String(Character.toChars((int)Long.parseLong(unicode32, 16)));
          }
          if (unicodeName != null) {
            //TOLATER: implement unicode character name escapes
          }
        }
        String unicode16 = escMatcher.group(5);
        if (unicode16 != null) {
          str = new String(new char[]{(char)Integer.parseInt(unicode16, 16)});
        }
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
            if (!consumer.process(off, off + 6, Character.toString(u))) {
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
            if (!consumer.process(off, off + 10, Character.toString(u))) {
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

  public PyType getType(@NotNull TypeEvalContext context) {
    final List<ASTNode> nodes = getStringNodes();
    if (nodes.size() > 0) {
      String text = getStringNodes().get(0).getText();
      if (LanguageLevel.forElement(this).isPy3K()) {
        if (isBytes(text)) {
          return PyBuiltinCache.getInstance(this).getObjectType("bytes");
        }
      }
      else {
        if (isUnicode(text)) {
          return PyBuiltinCache.getInstance(this).getObjectType("unicode");
        }
      }
    }
    return PyBuiltinCache.getInstance(this).getStrType();
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
  }

  public List<Pair<PsiElement, TextRange>> getInjectedPsi() {
    return InjectedLanguageUtil.getInjectedPsiFiles(this);
  }

  public void processInjectedPsi(@NotNull InjectedPsiVisitor visitor) {
    InjectedLanguageUtil.enumerate(this, visitor);
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

  private interface TextRangeConsumer {
    boolean process(int startOffset, int endOffset, String value);
  }

  private static class StringLiteralTextEscaper extends LiteralTextEscaper<PyStringLiteralExpression> {
    private final PyStringLiteralExpressionImpl myHost;

    protected StringLiteralTextEscaper(@NotNull PyStringLiteralExpressionImpl host) {
      super(host);
      myHost = host;
    }

    @Override
    public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull final StringBuilder outChars) {
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
      return true;
    }

    @Override
    public int getOffsetInHost(final int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
      final Ref<Integer> offsetInDecodedRef = new Ref<Integer>(offsetInDecoded);
      final Ref<Integer> result = new Ref<Integer>(-1);
      myHost.iterateCharacterRanges(new TextRangeConsumer() {
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

    @Override
    public boolean isOneLine() {
      return false;
    }
  }

  public boolean characterNeedsEscaping(char c) {
    return c == ']' || c == '}' || c == '\"';
  }

  public boolean supportsPerl5EmbeddedComments() {
    return true;
  }

  public boolean supportsPossessiveQuantifiers() {
    return false;
  }

  public boolean supportsPythonNamedGroups() {
    return true;
  }

  public boolean supportsPythonConditionalRefs() {
    return true;
  }

  public boolean supportsRubyNamedGroups() {
    return false;
  }
}
