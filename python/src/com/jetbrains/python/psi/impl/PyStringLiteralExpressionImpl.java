package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.tree.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.PyType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PyStringLiteralExpressionImpl extends PyElementImpl implements PyStringLiteralExpression {
  private static final Pattern PATTERN_STRING =
      Pattern.compile("(u)?(r)?(\"\"\"|\"|'''|')(.*?)(\\3)?", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern PATTERN_ESCAPE = Pattern
      .compile("\\\\(\n|\\\\|'|\"|a|b|f|n|r|t|v|([0-8]{1,3})|x([0-9a-fA-F]{1,2})" + "|N(\\{.*?\\})|u([0-9a-fA-F]){4}|U([0-9a-fA-F]{8}))");
  private final Map<String, String> escapeMap = initializeEscapeMap();
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
        Matcher m = getTextMatcher(node.getText());
        int nodeOffset = node.getStartOffset() - elStart;
        ranges.add(TextRange.from(nodeOffset + m.start(4), nodeOffset + m.end(4)));
      }
      valueTextRanges = Collections.unmodifiableList(ranges);
    }
    return valueTextRanges;
  }

  public List<EvaluatedTextRange> getStringValueCharacterRanges() {
    int elStart = getTextRange().getStartOffset();
    List<EvaluatedTextRange> ranges = new ArrayList<EvaluatedTextRange>(100);
    for (ASTNode child : getStringNodes()) {
      Matcher m = getTextMatcher(child.getText());
      int offset = child.getTextRange().getStartOffset() - elStart + m.start(4);
      String undecoded = m.group(4);
      ranges.addAll(getEvaluatedTextRanges(undecoded, offset, m.group(2) != null, m.group(1) != null));
    }
    return Collections.unmodifiableList(ranges);
  }

  public List<ASTNode> getStringNodes() {
    return Arrays.asList(getNode().getChildren(TokenSet.create(PyTokenTypes.STRING_LITERAL)));
  }

  private static Matcher getTextMatcher(CharSequence text) {
    Matcher m = PATTERN_STRING.matcher(text);
    boolean matches = m.matches();
    assert matches : text;
    return m;
  }

  public String getStringValue() {
    //ASTNode child = getNode().getFirstChildNode();
    //assert child != null;
    if (stringValue == null) {
      StringBuilder out = new StringBuilder();
      List<EvaluatedTextRange> ranges = getStringValueCharacterRanges();
      for (EvaluatedTextRange range : ranges) {
        out.append(range.getValue());
      }
      stringValue = out.toString();
    }
    return stringValue;
  }

  private List<EvaluatedTextRange> getEvaluatedTextRanges(String undecoded, int off, boolean raw, boolean unicode) {
    Matcher escMatcher = PATTERN_ESCAPE.matcher(undecoded);
    List<EvaluatedTextRange> ranges = new ArrayList<EvaluatedTextRange>();
    int index = 0;
    while (escMatcher.find(index)) {
      for (int i = index; i < escMatcher.start(); i++) {
        ranges.add(new EvaluatedTextRange(TextRange.from(off + i, 1), undecoded.charAt(i)));
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
        ranges.add(new EvaluatedTextRange(TextRange.from(off + start, end - start), str));
      }
      index = escMatcher.end();
    }
    for (int i = index; i < undecoded.length(); i++) {
      ranges.add(new EvaluatedTextRange(TextRange.from(off + i, 1), undecoded.charAt(i)));
    }
    return ranges;
  }

  @Override
  public String toString() {
    return super.toString() + ": " + getStringValue();
  }

  public static TextRange getTextRange(List<EvaluatedTextRange> ranges, int start, int end) {
    assert start <= end : start + "," + end;
    if (start == ranges.size()) {
      // it's an empty string at the end of the string
      return TextRange.from(ranges.get(start - 1).getRange().getEndOffset(), 0);
    }
    int startOffset = ranges.get(start).getRange().getStartOffset();
    int endOffset = ranges.get(end - 1).getRange().getEndOffset();
    int length = endOffset - startOffset;
    return TextRange.from(startOffset, length);
  }

  public PyType getType() {
    return PyBuiltinCache.getInstance(this).getStrType(); // TODO: detect unicode vs byte
  }

  @NotNull
  public PsiReference[] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this, PyStringLiteralExpression.class);
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

  private static class StringLiteralTextEscaper extends LiteralTextEscaper<PyStringLiteralExpression> {
    private final PyStringLiteralExpression myHost;

    protected StringLiteralTextEscaper(@NotNull PyStringLiteralExpression host) {
      super(host);
      myHost = host;
    }

    @Override
    public boolean decode(@NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
      // TODO this is far too slow
      final List<EvaluatedTextRange> characterRanges = myHost.getStringValueCharacterRanges();
      for (EvaluatedTextRange range : characterRanges) {
        final TextRange xsect = rangeInsideHost.intersection(range.getRange());
        if (xsect != null && xsect.getLength() > 0) {
          outChars.append(range.getValue());
        }
      }
      return true;
    }

    @Override
    public int getOffsetInHost(int offsetInDecoded, @NotNull TextRange rangeInsideHost) {
      final List<EvaluatedTextRange> characterRanges = myHost.getStringValueCharacterRanges();

      for (EvaluatedTextRange range : characterRanges) {
        if (offsetInDecoded < range.getValue().length()) {
          return range.getRange().getStartOffset() + offsetInDecoded;
        }
        offsetInDecoded -= range.getValue().length();
      }
      return -1;
    }

    @Override
    public boolean isOneLine() {
      return false;
    }
  }
}
