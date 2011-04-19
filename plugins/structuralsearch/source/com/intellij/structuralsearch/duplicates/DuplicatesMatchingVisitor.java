package com.intellij.structuralsearch.duplicates;

import com.intellij.dupLocator.DuplocatorSettings;
import com.intellij.dupLocator.NodeSpecificHasher;
import com.intellij.dupLocator.treeHash.TreeHashResult;
import com.intellij.dupLocator.util.PsiFragment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.structuralsearch.StructuralSearchProfileImpl;
import com.intellij.structuralsearch.equivalence.ChildRole;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptor;
import com.intellij.structuralsearch.equivalence.EquivalenceDescriptorProvider;
import com.intellij.structuralsearch.impl.matcher.AbstractMatchingVisitor;
import com.intellij.structuralsearch.impl.matcher.filters.NodeFilter;
import com.intellij.structuralsearch.impl.matcher.handlers.SkippingHandler;
import com.intellij.structuralsearch.impl.matcher.iterators.FilteringNodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.NodeIterator;
import com.intellij.structuralsearch.impl.matcher.iterators.SiblingNodeIterator;
import com.intellij.util.containers.HashMap;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 */
public class DuplicatesMatchingVisitor extends AbstractMatchingVisitor {
  private final NodeSpecificHasher myNodeSpecificHasher;
  private final DuplocatorSettings mySettings;
  private final Set<ChildRole> mySkippedRoles;
  private final NodeFilter myNodeFilter;
  private final int myDiscardCost;
  private final SSRTreeHasher myTreeHasher;
  private final Map<PsiElement, TreeHashResult> myPsiElement2HashAndCost = new HashMap<PsiElement, TreeHashResult>();

  public DuplicatesMatchingVisitor(NodeSpecificHasher nodeSpecificHasher,
                                   Set<ChildRole> skippedRoles,
                                   NodeFilter nodeFilter,
                                   int discardCost) {
    myNodeSpecificHasher = nodeSpecificHasher;
    mySkippedRoles = skippedRoles;
    mySettings = DuplocatorSettings.getInstance();
    myNodeFilter = nodeFilter;
    myDiscardCost = discardCost;
    myTreeHasher = new SSRTreeHasher(null, mySettings) {
      @Override
      protected TreeHashResult hash(@NotNull PsiElement root, PsiFragment upper, @NotNull NodeSpecificHasher hasher) {
        TreeHashResult result = myPsiElement2HashAndCost.get(root);
        if (result == null) {
          result = super.hash(root, upper, hasher);
          myPsiElement2HashAndCost.put(root, result);
        }
        return result;
      }
    };
  }

  @Override
  public boolean matchSequentially(NodeIterator nodes, NodeIterator nodes2) {
    if (!nodes.hasNext() || !nodes2.hasNext()) {
      return !nodes.hasNext() && !nodes2.hasNext();
    }

    skipIfNeccessary(nodes, nodes2);
    skipIfNeccessary(nodes2, nodes);

    if (!nodes.hasNext() || !nodes2.hasNext()) {
      return !nodes.hasNext() && !nodes2.hasNext();
    }

    if (!match(nodes.current(), nodes2.current())) {
      return false;
    }

    nodes.advance();
    nodes2.advance();

    return matchSequentially(nodes, nodes2);
  }

  private static void skipIfNeccessary(NodeIterator nodes, NodeIterator nodes2) {
    while (StructuralSearchProfileImpl.shouldSkip(nodes2.current(), nodes.current())) {
      nodes2.advance();
    }
  }

  @Override
  public boolean match(PsiElement element1, PsiElement element2) {
    if (element1 == null || element2 == null) {
      return element1 == element2;
    }

    final int cost1 = myTreeHasher.hash(element1, null, myNodeSpecificHasher).getCost();
    final int cost2 = myTreeHasher.hash(element2, null, myNodeSpecificHasher).getCost();

    if (cost1 < myDiscardCost || cost2 < myDiscardCost) {
      return true;
    }

    final EquivalenceDescriptorProvider descriptorProvider = EquivalenceDescriptorProvider.getInstance(element1);
    EquivalenceDescriptor descriptor1 = descriptorProvider != null ? descriptorProvider.buildDescriptor(element1) : null;
    EquivalenceDescriptor descriptor2 = descriptorProvider != null ? descriptorProvider.buildDescriptor(element2) : null;

    PsiElement newElement1 = SkippingHandler.skipNodeIfNeccessary(element1, descriptor1, myNodeFilter);
    PsiElement newElement2 = SkippingHandler.skipNodeIfNeccessary(element2, descriptor2, myNodeFilter);

    if (newElement1 != element1 || newElement2 != element2) {
      return match(newElement1, newElement2);
    }

    /*if (descriptorProvider != null) {
      if (newElement1 != element1) {
        descriptor1 = descriptorProvider.buildDescriptor(newElement1);
        element1 = newElement1;
      }
      if (newElement2 != element2) {
        descriptor2 = descriptorProvider.buildDescriptor(newElement2);
        element2 = newElement2;
      }
    }*/

    if (!element1.getClass().equals(element2.getClass())) {
      return false;
    }

    if (descriptor1 != null && descriptor2 != null) {
      return StructuralSearchProfileImpl.match(descriptor1, descriptor2, this, mySkippedRoles);
    }

    if (element1 instanceof LeafElement) {
      IElementType elementType1 = ((LeafElement)element1).getElementType();
      IElementType elementType2 = ((LeafElement)element2).getElementType();

      if (!mySettings.DISTINGUISH_LITERALS &&
          descriptorProvider != null &&
          descriptorProvider.getLiterals().contains(elementType1) &&
          descriptorProvider.getLiterals().contains(elementType2)) {
        return true;
      }
      return element1.getText().equals(element2.getText());
    }

    if (element1.getFirstChild() == null && element1.getTextLength() == 0) {
      return element2.getFirstChild() == null && element2.getTextLength() == 0;
    }

    return matchSequentially(new FilteringNodeIterator(new SiblingNodeIterator(element1.getFirstChild()), getNodeFilter()),
                             new FilteringNodeIterator(new SiblingNodeIterator(element2.getFirstChild()), getNodeFilter()));
  }

  @Override
  protected boolean doMatchInAnyOrder(NodeIterator it1, NodeIterator it2) {
    final List<PsiElement> elements1 = new ArrayList<PsiElement>();
    final List<PsiElement> elements2 = new ArrayList<PsiElement>();

    while (it1.hasNext()) {
      final PsiElement element = it1.current();
      if (element != null) {
        elements1.add(element);
      }
      it1.advance();
    }

    while (it2.hasNext()) {
      final PsiElement element = it2.current();
      if (element != null) {
        elements2.add(element);
      }
      it2.advance();
    }

    if (elements1.size() != elements2.size()) {
      return false;
    }

    final TIntObjectHashMap<List<PsiElement>> hash2element = new TIntObjectHashMap<List<PsiElement>>(elements1.size());

    for (PsiElement element : elements1) {
      final TreeHashResult result = myTreeHasher.hash(element, null, myNodeSpecificHasher);
      if (result != null) {
        final int hash = result.getHash();

        List<PsiElement> list = hash2element.get(hash);
        if (list == null) {
          list = new ArrayList<PsiElement>();
          hash2element.put(hash, list);
        }
        list.add(element);
      }
    }

    for (PsiElement element : elements2) {
      final TreeHashResult result = myTreeHasher.hash(element, null, myNodeSpecificHasher);
      if (result != null) {
        final int hash = result.getHash();
        final List<PsiElement> list = hash2element.get(hash);
        if (list == null) {
          return false;
        }

        boolean found = false;
        for (Iterator<PsiElement> it = list.iterator(); it.hasNext();) {
          if (match(element, it.next())) {
            it.remove();
            found = true;
          }
        }

        if (!found) {
          return false;
        }

        if (list.size() == 0) {
          hash2element.remove(hash);
        }
      }
    }

    return hash2element.size() == 0;
  }

  @NotNull
  @Override
  protected NodeFilter getNodeFilter() {
    return myNodeFilter;
  }
}
