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
import com.intellij.structuralsearch.equivalence.*;
import com.intellij.structuralsearch.impl.matcher.handlers.SkippingHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
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

    final PsiElement element = SkippingHandler.getOnlyChild(root, SSRNodeSpecificHasher.getNodeFilter());
    if (element != root) {
      final TreeHashResult result = hash(element, upper, hasher);
      final int cost = hasher.getNodeCost(root);
      return new TreeHashResult(result.getHash(), result.getCost() + cost, result.getFragment());
    }

    return computeElementHash(element, upper, hasher);
  }

  private TreeHashResult computeHash(PsiElement element,
                                     PsiFragment parent,
                                     EquivalenceDescriptor descriptor,
                                     NodeSpecificHasher nodeSpecificHasher) {

    final PsiElement element2 = SkippingHandler.skipNodeIfNeccessary(element, descriptor, SSRNodeSpecificHasher.getNodeFilter());
    final boolean canSkip = element2 != element;

    final PsiFragment fragment = new TreePsiFragment(nodeSpecificHasher, element, 0);

    if (parent != null) {
      fragment.setParent(parent);
    }

    int hash = canSkip ? 0 : nodeSpecificHasher.getNodeHash(element);
    int cost = nodeSpecificHasher.getNodeCost(element);

    for (SingleChildDescriptor childDescriptor : descriptor.getSingleChildDescriptors()) {
      final Pair<Integer, Integer> childHashResult = computeHash(childDescriptor, fragment, nodeSpecificHasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (MultiChildDescriptor childDescriptor : descriptor.getMultiChildDescriptors()) {
      final Pair<Integer, Integer> childHashResult = computeHash(childDescriptor, fragment, nodeSpecificHasher);
      hash = hash * 31 + childHashResult.first;
      cost += childHashResult.second;
    }

    for (Object constant : descriptor.getConstants()) {
      final int constantHash = constant != null ? constant.hashCode() : 0;
      hash = hash * 31 + constantHash;
    }

    for (PsiElement[] codeBlock : descriptor.getCodeBlocks()) {
      final TreeHashResult childHashResult = hashCodeBlock(Arrays.asList(codeBlock), fragment, nodeSpecificHasher);
      hash = hash * 31 + childHashResult.getHash();
      cost += childHashResult.getCost();
    }

    if (myCallback != null) {
      myCallback.add(hash, cost, fragment);
    }
    return new TreeHashResult(hash, cost, fragment);
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
      final ChildRole role = childDescriptor.getRole();
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
