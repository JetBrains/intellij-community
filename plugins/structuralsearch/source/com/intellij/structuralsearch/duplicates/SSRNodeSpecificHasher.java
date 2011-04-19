package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.structuralsearch.StructuralSearchProfileImpl;
import com.intellij.structuralsearch.equivalence.ChildRole;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.SiblingNodeIterator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class SSRNodeSpecificHasher extends NodeSpecificHasher {
  private final SSRTreeHasher myTreeHasher;
  private final DuplocatorSettings mySettings;
  private final Set<ChildRole> mySkippedRoles;

  private static final NodeFilter ourNodeFilter = new NodeFilter() {
      @Override
      public boolean accepts(PsiElement element) {
        return StructuralSearchProfileImpl.isLexicalNode(element) ||
               !DuplocatorSettings.getInstance().DISTINGUISH_LITERALS && isLiteral(element);
      }
    };

  public SSRNodeSpecificHasher(@NotNull final DuplocatorSettings settings,
                               @NotNull DuplocatorHashCallback callback) {
    myTreeHasher = new SSRTreeHasher(callback, settings);
    mySettings = settings;
    mySkippedRoles = getSkippedRoles(settings);
  }

  @NotNull
  public static NodeFilter getNodeFilter() {
    return ourNodeFilter;
  }

  private static Set<ChildRole> getSkippedRoles(DuplocatorSettings settings) {
    final Set<ChildRole> result = EnumSet.noneOf(ChildRole.class);

    if (!settings.DISTINGUISH_FIELDS) {
      result.add(ChildRole.FIELD_NAME);
    }
    if (!settings.DISTINGUISH_METHODS) {
      result.add(ChildRole.FUNCTION_NAME);
    }
    if (!settings.DISTINGUISH_VARIABLES) {
      result.add(ChildRole.VARIABLE_NAME);
    }
    return result;
  }

  @Override
  public int getNodeHash(PsiElement node) {
    if (node == null) {
      return 0;
    }
    if (node instanceof PsiWhiteSpace || node instanceof PsiErrorElement) {
      return 0;
    }
    else if (node instanceof LeafElement) {
      if (!mySettings.DISTINGUISH_LITERALS && isLiteral(node)) {
        return 0;
      }
      return node.getText().hashCode();

    }
    return node.getClass().getName().hashCode();
  }

  private static boolean isLiteral(PsiElement node) {
    if (node instanceof LeafElement) {
      final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(node);
      if (descriptorProvider != null) {
        final IElementType elementType = ((LeafElement)node).getElementType();
        if (descriptorProvider.getLiterals().contains(elementType)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public int getNodeCost(PsiElement node) {
    if (node == null) {
      return 0;
    }

    EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(node);
    if (descriptorProvider != null) {
      return descriptorProvider.getNodeCost(node);
    }

    if (node instanceof LeafElement && !ourNodeFilter.accepts(node)) {
      return node.getTextLength() / 5 + 1;
    }

    return 0;
  }

  @Override
  public List<PsiElement> getNodeChildren(PsiElement node) {
    final List<PsiElement> result = new ArrayList<PsiElement>();

    final FilteringNodeIterator it = new FilteringNodeIterator(new SiblingNodeIterator(node.getFirstChild()), ourNodeFilter);
    while (it.hasNext()) {
      result.add(it.current());
      it.advance();
    }

    return result;
  }

  @Override
  public boolean areNodesEqual(@NotNull PsiElement node1, @NotNull PsiElement node2) {
    return false;
  }

  @Override
  public boolean areTreesEqual(@NotNull PsiElement root1, @NotNull PsiElement root2, int discardCost) {
    // todo: support discard cost
    return new DuplicatesMatchingVisitor(this, mySkippedRoles, ourNodeFilter, discardCost).match(root1, root2);
  }

  @Override
  public boolean checkDeep(PsiElement node1, PsiElement node2) {
    // todo: try to optimize this
    return true;
  }

  @Override
  public void visitNode(@NotNull PsiElement node) {
    if (mySettings.SELECTED_PROFILES.contains(node.getLanguage().getDisplayName())) {
      myTreeHasher.hash(node, this);
    }
  }

  @Override
  public void hashingFinished() {
  }
}
