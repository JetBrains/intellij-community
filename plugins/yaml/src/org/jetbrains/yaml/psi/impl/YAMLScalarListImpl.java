package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.YAMLUtil;
import org.jetbrains.yaml.psi.YAMLScalarList;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author oleg
 * @see <http://www.yaml.org/spec/1.2/spec.html#id2795688>
 */
public class YAMLScalarListImpl extends YAMLBlockScalarImpl implements YAMLScalarList {
  public YAMLScalarListImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  protected IElementType getContentType() {
    return YAMLTokenTypes.SCALAR_LIST;
  }

  @NotNull
  @Override
  protected String getRangesJoiner(@NotNull CharSequence text, @NotNull List<TextRange> contentRanges, int indexBefore) {
    return "";
  }

  @NotNull
  @Override
  public String getTextValue(@Nullable TextRange rangeInHost) {
    String value = super.getTextValue(rangeInHost);
    if (!StringUtil.isEmptyOrSpaces(value) && getChompingIndicator() == ChompingIndicator.KEEP && isEnding(rangeInHost)) {
      value += "\n";
    }
    return value;
  }

  @Override
  protected boolean shouldIncludeEolInRange(ASTNode child) {
    if (getChompingIndicator() == ChompingIndicator.KEEP) return true;
    
    if (isEol(child) && isEolOrNull(child.getTreeNext())) {
      return false;
    }
    ASTNode next = TreeUtil.findSibling(child.getTreeNext(), NON_SPACE_VALUES);
    if (isEol(next) && isEolOrNull(TreeUtil.findSibling(next.getTreeNext(), NON_SPACE_VALUES)) && getChompingIndicator() == ChompingIndicator.STRIP) {
      return false;
    }

    return true;
  }
  
  private static final TokenSet NON_SPACE_VALUES = TokenSet.orSet(YAMLElementTypes.SCALAR_VALUES, YAMLElementTypes.EOL_ELEMENTS);

  @Override
  protected List<Pair<TextRange, String>> getEncodeReplacements(@NotNull CharSequence input) throws IllegalArgumentException {
    int indent = locateIndent();
    if (indent == 0) {
      indent = YAMLUtil.getIndentToThisElement(this) + DEFAULT_CONTENT_INDENT;
    }
    final String indentString = StringUtil.repeatSymbol(' ', indent);

    final List<Pair<TextRange, String>> result = new ArrayList<>();
    for (int i = 0; i < input.length(); ++i) {
      if (input.charAt(i) == '\n') {
        result.add(Pair.create(TextRange.from(i, 1), "\n" + indentString));
      }
    }

    return result;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    String original = getNode().getText();
    int commonPrefixLength = StringUtil.commonPrefixLength(original, text);
    int commonSuffixLength = StringUtil.commonSuffixLength(original, text);
    int indent = locateIndent();

    ASTNode scalarEol = getNode().findChildByType(YAMLTokenTypes.SCALAR_EOL);
    if (scalarEol == null) {
      // a very strange situation
      return super.updateText(text);
    }

    int eolOffsetInParent = scalarEol.getStartOffsetInParent();

    int startContent = eolOffsetInParent + indent + 1;
    if (startContent >= commonPrefixLength) {
      // a very strange situation
      return super.updateText(text);
    }

    String originalRowPrefix = original.substring(startContent, commonPrefixLength);
    String indentString = StringUtil.repeatSymbol(' ', indent);

    String prefix = originalRowPrefix.replaceAll("\n" + indentString, "\n");
    String suffix = text.substring(text.length() - commonSuffixLength).replaceAll("\n" + indentString, "\n");

    String result = prefix + text.substring(commonPrefixLength, text.length() - commonSuffixLength) + suffix;
    return super.updateText(result);
  }

  @Override
  public String toString() {
    return "YAML scalar list";
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof YamlPsiElementVisitor) {
      ((YamlPsiElementVisitor)visitor).visitScalarList(this);
    }
    else {
      super.accept(visitor);
    }
  }
}