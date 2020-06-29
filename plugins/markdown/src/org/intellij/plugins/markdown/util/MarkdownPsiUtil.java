package org.intellij.plugins.markdown.util;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.Consumer;
import com.intellij.util.NullableConsumer;
import org.intellij.plugins.markdown.lang.psi.MarkdownPsiElement;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownFile;
import org.intellij.plugins.markdown.lang.psi.impl.MarkdownHeaderImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

import static org.intellij.plugins.markdown.lang.MarkdownTokenTypeSets.*;

public class MarkdownPsiUtil {
  public static final TokenSet PRESENTABLE_TYPES = HEADERS;
  public static final TokenSet TRANSPARENT_CONTAINERS = TokenSet.create(MARKDOWN_FILE, UNORDERED_LIST, ORDERED_LIST, LIST_ITEM, BLOCK_QUOTE);
  private static final List<TokenSet> HEADER_ORDER = Arrays.asList(
    TokenSet.create(MARKDOWN_FILE_ELEMENT_TYPE),
    HEADER_LEVEL_1_SET,
    HEADER_LEVEL_2_SET,
    HEADER_LEVEL_3_SET,
    HEADER_LEVEL_4_SET,
    HEADER_LEVEL_5_SET,
    HEADER_LEVEL_6_SET);

  public static boolean isNewLine(@NotNull PsiElement element) {
    return WHITE_SPACES.contains(element.getNode().getElementType()) && element.getText().equals("\n");
  }

  /*
   * nextHeaderConsumer 'null' means reaching EOF
   */
  public static void processContainer(@Nullable PsiElement myElement,
                                      @NotNull Consumer<? super PsiElement> consumer,
                                      @NotNull NullableConsumer<? super PsiElement> nextHeaderConsumer) {
    if (myElement == null) return;

    final PsiElement structureContainer = myElement instanceof MarkdownFile ? myElement.getFirstChild()
                                                                            : getParentOfType(myElement, TRANSPARENT_CONTAINERS);
    if (structureContainer == null) return;

    final MarkdownPsiElement currentHeader = myElement instanceof MarkdownHeaderImpl ? ((MarkdownHeaderImpl)myElement) : null;
    processContainer(structureContainer, currentHeader, currentHeader, consumer, nextHeaderConsumer);
  }

  private static void processContainer(@NotNull PsiElement container,
                                       @Nullable PsiElement sameLevelRestriction,
                                       @Nullable MarkdownPsiElement from,
                                       @NotNull Consumer<? super PsiElement> resultConsumer,
                                       @NotNull NullableConsumer<? super PsiElement> nextHeaderConsumer) {
    PsiElement nextSibling = from == null ? container.getFirstChild() : from.getNextSibling();
    PsiElement maxContentLevel = null;
    while (nextSibling != null) {
      if (TRANSPARENT_CONTAINERS.contains(PsiUtilCore.getElementType(nextSibling)) && maxContentLevel == null) {
        processContainer(nextSibling, null, null, resultConsumer, nextHeaderConsumer);
      }
      else if (nextSibling instanceof MarkdownHeaderImpl) {
        if (sameLevelRestriction != null && isSameLevelOrHigher(nextSibling, sameLevelRestriction)) {
          nextHeaderConsumer.consume(nextSibling);
          break;
        }

        if (maxContentLevel == null || isSameLevelOrHigher(nextSibling, maxContentLevel)) {
          maxContentLevel = nextSibling;

          final IElementType type = nextSibling.getNode().getElementType();
          if (PRESENTABLE_TYPES.contains(type)) {
            resultConsumer.consume(nextSibling);
          }
        }
      }

      nextSibling = nextSibling.getNextSibling();
      if (nextSibling == null) nextHeaderConsumer.consume(null);
    }
  }

  private static boolean isSameLevelOrHigher(@NotNull PsiElement psiA, @NotNull PsiElement psiB) {
    IElementType typeA = psiA.getNode().getElementType();
    IElementType typeB = psiB.getNode().getElementType();

    return headerLevel(typeA) <= headerLevel(typeB);
  }

  private static int headerLevel(@NotNull IElementType curLevelType) {
    for (int i = 0; i < HEADER_ORDER.size(); i++) {
      if (HEADER_ORDER.get(i).contains(curLevelType)) {
        return i;
      }
    }

    // not a header so return lowest level
    return Integer.MAX_VALUE;
  }

  @Nullable
  private static PsiElement getParentOfType(@NotNull PsiElement myElement, @NotNull TokenSet types) {
    final ASTNode parentNode = TreeUtil.findParent(myElement.getNode(), types);
    return parentNode == null ? null : parentNode.getPsi();
  }
}
