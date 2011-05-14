package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DuplicatesProfile;
import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.structuralsearch.StructuralSearchProfileBase;
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
  private final Set<PsiElementRole> mySkippedRoles;
  private SSRDuplicatesProfile myDuplicatesProfile;

  private final NodeFilter myNodeFilter = new NodeFilter() {
      @Override
      public boolean accepts(PsiElement element) {
        return StructuralSearchProfileBase.isIgnoredNode(element) ||
               !DuplocatorSettings.getInstance().DISTINGUISH_LITERALS && isLiteral(element);
      }
    };

  public SSRNodeSpecificHasher(@NotNull final DuplocatorSettings settings,
                               @NotNull DuplocatorHashCallback callback,
                               SSRDuplicatesProfile duplicatesProfile) {
    myTreeHasher = new SSRTreeHasher(callback, settings);
    mySettings = settings;
    mySkippedRoles = getSkippedRoles(settings);
    myDuplicatesProfile = duplicatesProfile;
  }

  @NotNull
  public NodeFilter getNodeFilter() {
    return myNodeFilter;
  }

  private static Set<PsiElementRole> getSkippedRoles(DuplocatorSettings settings) {
    final Set<PsiElementRole> result = EnumSet.noneOf(PsiElementRole.class);

    if (!settings.DISTINGUISH_FIELDS) {
      result.add(PsiElementRole.FIELD_NAME);
    }
    if (!settings.DISTINGUISH_METHODS) {
      result.add(PsiElementRole.FUNCTION_NAME);
    }
    if (!settings.DISTINGUISH_VARIABLES) {
      result.add(PsiElementRole.VARIABLE_NAME);
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

  private boolean isLiteral(PsiElement node) {
    if (node instanceof LeafElement) {
      final IElementType elementType = ((LeafElement)node).getElementType();
      if (myDuplicatesProfile.getLiterals().contains(elementType)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getNodeCost(PsiElement node) {
    return node != null ? myDuplicatesProfile.getNodeCost(node) : 0;
  }

  @Override
  public List<PsiElement> getNodeChildren(PsiElement node) {
    final List<PsiElement> result = new ArrayList<PsiElement>();

    final FilteringNodeIterator it = new FilteringNodeIterator(new SiblingNodeIterator(node.getFirstChild()), myNodeFilter);
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
    return new DuplicatesMatchingVisitor(this, mySkippedRoles, myNodeFilter, discardCost).match(root1, root2);
  }

  public SSRDuplicatesProfile getDuplicatesProfile() {
    return myDuplicatesProfile;
  }

  @Override
  public boolean checkDeep(PsiElement node1, PsiElement node2) {
    // todo: try to optimize this
    return true;
  }

  @Override
  public void visitNode(@NotNull PsiElement node) {
    final Language language = node.getLanguage();
    if (mySettings.SELECTED_PROFILES.contains(language.getDisplayName()) &&
        DuplicatesProfile.findProfileForLanguage(DuplicatesProfile.getAllProfiles(), language) == myDuplicatesProfile &&
        myDuplicatesProfile.isMyLanguage(language)) {

      myTreeHasher.hash(node, this);
    }
  }

  @Override
  public void hashingFinished() {
  }
}
