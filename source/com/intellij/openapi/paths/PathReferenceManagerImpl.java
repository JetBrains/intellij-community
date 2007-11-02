/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.paths;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.jsp.jspJava.JspXmlTagBase;
import com.intellij.psi.jsp.el.ELExpressionHolder;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class PathReferenceManagerImpl extends PathReferenceManager {
  private final StaticPathReferenceProvider myStaticProvider = new StaticPathReferenceProvider();
  private final PathReferenceProvider myGlobalPathsProvider = new GlobalPathReferenceProvider();
  private static final Comparator<PsiReference> START_OFFSET_COMPARATOR = new Comparator<PsiReference>() {
    public int compare(final PsiReference o1, final PsiReference o2) {
      return o1.getRangeInElement().getStartOffset() - o2.getRangeInElement().getStartOffset();
    }
  };

  @Nullable
  public PathReference getPathReference(@NotNull String path, @NotNull final Module module, @NotNull PsiElement element, PathReferenceProvider... additionalProviders) {
    PathReference pathReference;
    for (PathReferenceProvider provider : getProviders()) {
      pathReference = provider.getPathReference(path, element);
      if (pathReference != null) {
        return pathReference;
      }
    }
    for (PathReferenceProvider provider : additionalProviders) {
      pathReference = provider.getPathReference(path, element);
      if (pathReference != null) {
        return pathReference;
      }
    }
    pathReference = myStaticProvider.getPathReference(path, element);
    if (pathReference != null) {
      return pathReference;
    }
    return null;
  }

  @Nullable
  public PathReference getCustomPathReference(@NotNull String path, @NotNull Module module, @NotNull PsiElement element, PathReferenceProvider... providers) {
    for (PathReferenceProvider provider : providers) {
      PathReference reference = provider.getPathReference(path, element);
      if (reference != null) {
        return reference;
      }
    }
    return null;
  }

  @NotNull
  public PathReferenceProvider getGlobalWebPathReferenceProvider() {
    return myGlobalPathsProvider;
  }

  @NotNull
  public PathReferenceProvider createStaticPathReferenceProvider(final boolean relativePathsAllowed) {
    final StaticPathReferenceProvider provider = new StaticPathReferenceProvider();
    provider.setRelativePathsAllowed(relativePathsAllowed);
    return provider;
  }

  @NotNull
  public PsiReference[] createReferences(@NotNull final PsiElement psiElement,
                                         final boolean soft,
                                         boolean endingSlashNotAllowed,
                                         final boolean relativePathsAllowed,
                                         PathReferenceProvider... additionalProviders) {

    if (PsiTreeUtil.getChildOfAnyType(psiElement, ELExpressionHolder.class, JspXmlTagBase.class) != null) {
      return PsiReference.EMPTY_ARRAY;
    }
    List<PsiReference> mergedReferences = new ArrayList<PsiReference>();
    processProvider(psiElement, myGlobalPathsProvider, mergedReferences, soft);

    myStaticProvider.setEndingSlashNotAllowed(endingSlashNotAllowed);
    myStaticProvider.setRelativePathsAllowed(relativePathsAllowed);
    processProvider(psiElement, myStaticProvider, mergedReferences, soft);

    for (PathReferenceProvider provider : getProviders()) {
      processProvider(psiElement, provider, mergedReferences, soft);
    }
    for (PathReferenceProvider provider : additionalProviders) {
      processProvider(psiElement, provider, mergedReferences, soft);
    }

    return mergeReferences(psiElement, soft, mergedReferences);
  }

  @NotNull
  public PsiReference[] createCustomReferences(@NotNull PsiElement psiElement, boolean soft, PathReferenceProvider... providers) {
    List<PsiReference> references = new ArrayList<PsiReference>();
    for (PathReferenceProvider provider : providers) {
      boolean processed = processProvider(psiElement, provider, references, soft);
      if (processed) {
        break;
      }
    }
    return mergeReferences(psiElement, soft, references);
  }

  @NotNull
  public PsiReference[] createReferences(@NotNull PsiElement psiElement, final boolean soft, PathReferenceProvider... additionalProviders) {
    return createReferences(psiElement, soft, false, true, additionalProviders);
  }

  private static PsiReference[] mergeReferences(PsiElement element, final boolean soft, List<PsiReference> references) {
    List<PsiReference> resolvingRefs = new ArrayList<PsiReference>();
    List<PsiReference> nonResolvingRefs = new ArrayList<PsiReference>();

    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < references.size(); i++) {
      PsiReference reference = references.get(i);

      assert element.equals(reference.getElement());
      if (reference.resolve() != null) {
        resolvingRefs.add(reference);
      } else {
        nonResolvingRefs.add(reference);
      }
    }

    Collections.sort(resolvingRefs, START_OFFSET_COMPARATOR);
    Collections.sort(nonResolvingRefs, START_OFFSET_COMPARATOR);

    List<PsiReference> result = new ArrayList<PsiReference>(5);
    while (!resolvingRefs.isEmpty()) {
      final List<PsiReference> list = new ArrayList<PsiReference>(5);
      addToResult(element, soft, result, list, addIntersectingReferences(nonResolvingRefs, list, getFirstIntersectingReferences(resolvingRefs, list)));
    }

    while (!nonResolvingRefs.isEmpty()) {
      final SmartList<PsiReference> list = new SmartList<PsiReference>();
      final TextRange range = getFirstIntersectingReferences(nonResolvingRefs, list);
      int endOffset = range.getEndOffset();
      for (final PsiReference reference : list) {
        endOffset = Math.min(endOffset, reference.getRangeInElement().getEndOffset());
      }
      addToResult(element, soft, result, list, new TextRange(range.getStartOffset(), endOffset));
    }

    return result.toArray(new PsiReference[result.size()]);
  }

  private static void addToResult(final PsiElement element, final boolean soft, final List<PsiReference> result,
                                  final List<PsiReference> list,
                                  final TextRange range) {
    if (list.size() == 1) {
      result.add(list.get(0));
    } else {
      final PsiDynaReference psiDynaReference = new PsiDynaReference(element, soft);
      psiDynaReference.addReferences(list);
      psiDynaReference.setRangeInElement(range);
      result.add(psiDynaReference);
    }
  }

  private static TextRange addIntersectingReferences(List<PsiReference> set, List<PsiReference> toAdd, TextRange range) {
    int startOffset = range.getStartOffset();
    int endOffset = range.getStartOffset();
    for (Iterator<PsiReference> iterator = set.iterator(); iterator.hasNext();) {
      PsiReference reference = iterator.next();
      final TextRange rangeInElement = reference.getRangeInElement();
      if (intersect(range, rangeInElement)) {
        toAdd.add(reference);
        iterator.remove();
        startOffset = Math.min(startOffset, rangeInElement.getStartOffset());
        endOffset = Math.max(endOffset, rangeInElement.getEndOffset());
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  private static boolean intersect(final TextRange range1, final TextRange range2) {
    return range2.intersectsStrict(range1) || range2.intersects(range1) && (range1.isEmpty() || range2.isEmpty());
  }

  private static TextRange getFirstIntersectingReferences(List<PsiReference> set, List<PsiReference> toAdd) {
    int startOffset = Integer.MAX_VALUE;
    int endOffset = -1;
    for (Iterator<PsiReference> it = set.iterator(); it.hasNext();) {
      PsiReference reference = it.next();
      final TextRange range = reference.getRangeInElement();
      if (endOffset == -1 || range.getStartOffset() < endOffset) {
        startOffset = Math.min(startOffset, range.getStartOffset());
        endOffset = Math.max(range.getEndOffset(), endOffset);
        toAdd.add(reference);
        it.remove();
      }
      else {
        break;
      }
    }
    return new TextRange(startOffset, endOffset);
  }

  private static boolean processProvider(PsiElement psiElement, PathReferenceProvider provider, List<PsiReference> mergedReferences, boolean soft) {
    List<PsiReference> psiReferences = new ArrayList<PsiReference>();
    final boolean result = provider.createReferences(psiElement, psiReferences, soft);
    mergedReferences.addAll(psiReferences);
    return result;
  }

  private PathReferenceProvider[] getProviders() {
    return Extensions.getExtensions(PATH_REFERENCE_PROVIDER_EP);
  }
}
