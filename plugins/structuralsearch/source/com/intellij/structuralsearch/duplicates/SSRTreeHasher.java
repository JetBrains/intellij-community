package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.treeHash.AbstractTreeHasher;
import com.intellij.dupLocator.treeHash.DuplocatorHashCallback;
import com.intellij.dupLocator.treeHash.TreeHashResult;
import com.intellij.dupLocator.treeHash.TreePsiFragment;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.structuralsearch.equivalence.*;
import com.intellij.structuralsearch.impl.matcher.handlers.SkippingHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
class SSRTreeHasher extends AbstractTreeHasher {
  private final DuplocatorSettings mySettings;
  private final DuplocatorHashCallback myCallback;

  SSRTreeHasher(@Nullable DuplocatorHashCallback callback, DuplocatorSettings settings) {
    super(callback, settings.DISCARD_COST);
    myCallback = callback;
    mySettings = settings;
  }

  @Override
  protected TreeHashResult hash(@NotNull PsiElement root, PsiFragment upper, @NotNull NodeSpecificHasher hasher) {
    final TreeHashResult result = computeHash(root, upper, hasher);

    // todo: try to optimize (ex. compute cost and hash separately)
    if (result.getCost() < myDiscardCost) {
      return new TreeHashResult(0, result.getCost(), result.getFragment());
    }

    return result;
  }

  private TreeHashResult computeHash(PsiElement root, PsiFragment upper, NodeSpecificHasher hasher) {
    final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(root);

    if (descriptorProvider != null) {
      final EquivalenceDescriptor descriptor = descriptorProvider.buildDescriptor(root);

      if (descriptor != null) {
        return computeHash(root, upper, descriptor, hasher);
      }
    }

    if (root instanceof PsiFile) {
      return hashCodeBlock(hasher.getNodeChildren(root), upper, hasher, true);
    }

    final SSRNodeSpecificHasher ssrNodeSpecificHasher = (SSRNodeSpecificHasher)hasher;

    if (shouldBeAnonymized(root, ssrNodeSpecificHasher)) {
      return computeElementHash(root, upper, hasher);
    }

    final PsiElement element = SkippingHandler.getOnlyChild(root, ssrNodeSpecificHasher.getNodeFilter());
    if (element != root) {
      final TreeHashResult result = hash(element, upper, hasher);
      final int cost = hasher.getNodeCost(root);
      return new TreeHashResult(result.getHash(), result.getCost() + cost, result.getFragment());
    }

    return computeElementHash(element, upper, hasher);
  }

  @Override
  protected TreeHashResult computeElementHash(@NotNull PsiElement root, PsiFragment upper, NodeSpecificHasher hasher) {
    final List<PsiElement> children = hasher.getNodeChildren(root);
    final int size = children.size();
    final int[] childHashes = new int[size];
    final int[] childCosts = new int[size];

    final PsiFragment fragment = new TreePsiFragment(hasher, root, getCost(root));

    if (upper != null) {
      fragment.setParent(upper);
    }

    if (size == 0 && !(root instanceof LeafElement)) {
      // contains only whitespaces and other unmeaning children
      return new TreeHashResult(0, hasher.getNodeCost(root), fragment);
    }

    for (int i = 0; i < size; i++) {
      final TreeHashResult res = this.hash(children.get(i), fragment, hasher);
      childHashes[i] = res.getHash();
      childCosts[i] = res.getCost();
    }

    final int c = hasher.getNodeCost(root) + AbstractTreeHasher.vector(childCosts);
    final int h1 = hasher.getNodeHash(root);

    for (int i = 0; i < size; i++) {
      if (childCosts[i] <= myDiscardCost && ignoreChildHash(children.get(i))) {
        childHashes[i] = 0;
      }
    }

    int h = h1 + AbstractTreeHasher.vector(childHashes);

    if (shouldBeAnonymized(root, (SSRNodeSpecificHasher)hasher)) {
      h = 0;
    }

    if (myCallBack != null) {
      myCallBack.add(h, c, fragment);
    }

    return new TreeHashResult(h, c, fragment);
  }

  private TreeHashResult computeHash(PsiElement element,
                                     PsiFragment parent,
                                     EquivalenceDescriptor descriptor,
                                     NodeSpecificHasher hasher) {
    final SSRNodeSpecificHasher ssrHasher = (SSRNodeSpecificHasher)hasher;
    final PsiElement element2 = SkippingHandler.skipNodeIfNeccessary(element, descriptor, ssrHasher.getNodeFilter());
    final boolean canSkip = element2 != element;

    final PsiFragment fragment = new TreePsiFragment(hasher, element, 0);

    if (parent != null) {
      fragment.setParent(parent);
    }

    int hash = canSkip ? 0 : hasher.getNodeHash(element);
    int cost = hasher.getNodeCost(element);

    for (SingleChildDescriptor childDescriptor : descriptor.getSingleChildDescriptors()) {
      final Pair<Integer, Integer> childHashResult = computeHash(childDescriptor, fragment, hasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (MultiChildDescriptor childDescriptor : descriptor.getMultiChildDescriptors()) {
      final Pair<Integer, Integer> childHashResult = computeHash(childDescriptor, fragment, hasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (Object constant : descriptor.getConstants()) {
      final int constantHash = constant != null ? constant.hashCode() : 0;
      hash = hash * 31 + constantHash;
    }

    for (PsiElement[] codeBlock : descriptor.getCodeBlocks()) {
      final List<PsiElement> filteredBlock = filter(codeBlock, ssrHasher);
      final TreeHashResult childHashResult = hashCodeBlock(filteredBlock, fragment, hasher);
      hash = hash * 31 + childHashResult.getHash();
      cost += childHashResult.getCost();
    }

    if (myCallback != null) {
      myCallback.add(hash, cost, fragment);
    }
    return new TreeHashResult(hash, cost, fragment);
  }

  public static List<PsiElement> filter(PsiElement[] elements, SSRNodeSpecificHasher hasher) {
    List<PsiElement> filteredElements = new ArrayList<PsiElement>();
    for (PsiElement element : elements) {
      if (!hasher.getNodeFilter().accepts(element)) {
        filteredElements.add(element);
      }
    }
    return filteredElements;
  }

  @NotNull
  private Pair<Integer, Integer> computeHash(SingleChildDescriptor childDescriptor,
                                             PsiFragment parentFragment,
                                             NodeSpecificHasher nodeSpecificHasher) {

    final PsiElement element = childDescriptor.getElement();
    if (element == null) {
      return new Pair<Integer, Integer>(0, 0);
    }
    final Pair<Integer, Integer> result = doComputeHash(childDescriptor, parentFragment, nodeSpecificHasher);

    if (result != null) {
      final PsiElementRole role = ((SSRNodeSpecificHasher)nodeSpecificHasher).getDuplicatesProfile().getRole(element);
      if (role != null) {
        switch (role) {
          case VARIABLE_NAME:
            if (!mySettings.DISTINGUISH_VARIABLES) {
              return new Pair<Integer, Integer>(0, result.second);
            }
            break;
          case FIELD_NAME:
            if (!mySettings.DISTINGUISH_FIELDS) {
              return new Pair<Integer, Integer>(0, result.second);
            }
            break;
          case FUNCTION_NAME:
            if (!mySettings.DISTINGUISH_METHODS) {
              return new Pair<Integer, Integer>(0, result.second);
            }
            break;
        }
      }
    }
    return result;
  }

  private boolean shouldBeAnonymized(PsiElement element, SSRNodeSpecificHasher nodeSpecificHasher) {
    final PsiElementRole role = nodeSpecificHasher.getDuplicatesProfile().getRole(element);
    if (role != null) {
      switch (role) {
        case VARIABLE_NAME:
          if (!mySettings.DISTINGUISH_VARIABLES) {
            return true;
          }
          break;
        case FIELD_NAME:
          if (!mySettings.DISTINGUISH_FIELDS) {
            return true;
          }
          break;
        case FUNCTION_NAME:
          if (!mySettings.DISTINGUISH_METHODS) {
            return true;
          }
          break;
      }
    }
    return false;
  }

  @NotNull
  private Pair<Integer, Integer> doComputeHash(SingleChildDescriptor childDescriptor,
                                               PsiFragment parentFragment,
                                               NodeSpecificHasher nodeSpecificHasher) {
    final PsiElement element = childDescriptor.getElement();

    switch (childDescriptor.getType()) {
      case OPTIONALLY_IN_PATTERN:
      case DEFAULT:
        final TreeHashResult result = hash(element, parentFragment, nodeSpecificHasher);
        return new Pair<Integer, Integer>(result.getHash(), result.getCost());

      case CHILDREN_OPTIONALLY_IN_PATTERN:
      case CHILDREN:
        TreeHashResult[] childResults = computeHashesForChildren(element, parentFragment, nodeSpecificHasher);
        int[] hashes = getHashes(childResults);
        int[] costs = getCosts(childResults);

        int hash = AbstractTreeHasher.vector(hashes, 31);
        int cost = AbstractTreeHasher.vector(costs);

        return new Pair<Integer, Integer>(hash, cost);

      case CHILDREN_IN_ANY_ORDER:
        childResults = computeHashesForChildren(element, parentFragment, nodeSpecificHasher);
        hashes = getHashes(childResults);
        costs = getCosts(childResults);

        hash = AbstractTreeHasher.vector(hashes);
        cost = AbstractTreeHasher.vector(costs);

        return new Pair<Integer, Integer>(hash, cost);

      default:
        return new Pair<Integer, Integer>(0, 0);
    }
  }

  @NotNull
  private Pair<Integer, Integer> computeHash(MultiChildDescriptor childDescriptor,
                                     PsiFragment parentFragment,
                                     NodeSpecificHasher nodeSpecificHasher) {
    final PsiElement[] elements = childDescriptor.getElements();

    if (elements == null) {
      return new Pair<Integer, Integer>(0, 0);
    }

    switch (childDescriptor.getType()) {

      case OPTIONALLY_IN_PATTERN:
      case DEFAULT:
        TreeHashResult[] childResults = computeHashes(elements, parentFragment, nodeSpecificHasher);
        int[] hashes = getHashes(childResults);
        int[] costs = getCosts(childResults);

        int hash = AbstractTreeHasher.vector(hashes, 31);
        int cost = AbstractTreeHasher.vector(costs);

        return new Pair<Integer, Integer>(hash, cost);

      case IN_ANY_ORDER:
        childResults = computeHashes(elements, parentFragment, nodeSpecificHasher);
        hashes = getHashes(childResults);
        costs = getCosts(childResults);

        hash = AbstractTreeHasher.vector(hashes);
        cost = AbstractTreeHasher.vector(costs);

        return new Pair<Integer, Integer>(hash, cost);

      default:
        return new Pair<Integer, Integer>(0, 0);
    }
  }

  @NotNull
  private TreeHashResult[] computeHashesForChildren(PsiElement element,
                                                    PsiFragment parentFragment,
                                                    NodeSpecificHasher nodeSpecificHasher) {
    final List<TreeHashResult> result = new ArrayList<TreeHashResult>();

    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      final TreeHashResult childResult = hash(element, parentFragment, nodeSpecificHasher);
      result.add(childResult);
    }
    return result.toArray(new TreeHashResult[result.size()]);
  }

  @NotNull
  private TreeHashResult[] computeHashes(PsiElement[] elements,
                                         PsiFragment parentFragment,
                                         NodeSpecificHasher nodeSpecificHasher) {
    TreeHashResult[] result = new TreeHashResult[elements.length];

    for (int i = 0; i < elements.length; i++) {
      result[i] = hash(elements[i], parentFragment, nodeSpecificHasher);
    }

    return result;
  }

  private static int[] getHashes(TreeHashResult[] results) {
    int[] hashes = new int[results.length];

    for (int i = 0; i < results.length; i++) {
      hashes[i] = results[i].getHash();
    }

    return hashes;
  }

  private static int[] getCosts(TreeHashResult[] results) {
    int[] costs = new int[results.length];

    for (int i = 0; i < results.length; i++) {
      costs[i] = results[i].getCost();
    }

    return costs;
  }
}
