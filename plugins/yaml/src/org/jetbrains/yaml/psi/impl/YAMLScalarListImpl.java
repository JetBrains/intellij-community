package org.jetbrains.yaml.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.yaml.YAMLElementTypes;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLBlockScalar;
import org.jetbrains.yaml.psi.YAMLScalarList;
import org.jetbrains.yaml.psi.YamlPsiElementVisitor;

import java.util.List;

import static org.jetbrains.yaml.psi.impl.YAMLBlockScalarImplKt.isEol;

/**
 * @author oleg
 * @see <http://www.yaml.org/spec/1.2/spec.html#id2795688>
 */
public class YAMLScalarListImpl extends YAMLBlockScalarImpl implements YAMLScalarList, YAMLBlockScalar {
  public YAMLScalarListImpl(@NotNull final ASTNode node) {
    super(node);
  }

  @NotNull
  @Override
  protected IElementType getContentType() {
    return YAMLTokenTypes.SCALAR_LIST;
  }

  @Override
  public @NotNull YamlScalarTextEvaluator<YAMLScalarListImpl> getTextEvaluator() {
    return new YAMLBlockScalarTextEvaluator<>(this) {

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

        if (isEol(child) &&
            isEolOrNull(child.getTreeNext()) &&
            !(YAMLTokenTypes.INDENT.equals(ObjectUtils.doIfNotNull(child.getTreePrev(), ASTNode::getElementType)) &&
              myHost.getLinesNodes().size() <= 2)) {
          return false;
        }

        ASTNode next = TreeUtil.findSibling(child.getTreeNext(), NON_SPACE_VALUES);
        if (isEol(next) &&
            isEolOrNull(TreeUtil.findSibling(next.getTreeNext(), NON_SPACE_VALUES)) &&
            getChompingIndicator() == ChompingIndicator.STRIP) {
          return false;
        }

        return true;
      }

      private final TokenSet NON_SPACE_VALUES = TokenSet.orSet(YAMLElementTypes.SCALAR_VALUES, YAMLElementTypes.EOL_ELEMENTS);
    };
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
    if (startContent > commonPrefixLength) {
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