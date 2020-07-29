// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.util;

import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IdempotenceChecker;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;

/**
* @author peter
*/
final class InclusionProvider implements CachedValueProvider<PsiElement[]> {
  private final XmlTag myXincludeTag;

  InclusionProvider(XmlTag xincludeTag) {
    myXincludeTag = xincludeTag;
  }

  public static PsiElement @NotNull [] getIncludedTags(XmlTag xincludeTag) {
    if (!XmlTagImpl.shouldProcessIncludesNow()) {
      IdempotenceChecker.logTrace("!shouldProcessIncludesNow");
      return PsiElement.EMPTY_ARRAY;
    }
    return CachedValuesManager.getCachedValue(xincludeTag, new InclusionProvider(xincludeTag));
  }

  @Override
  public Result<PsiElement[]> compute() {
    PsiElement[] result = RecursionManager.doPreventingRecursion(myXincludeTag, true, () -> computeInclusion(myXincludeTag));
    if (result == null) {
      IdempotenceChecker.logTrace("InclusionProvider recursion prevented");
    }
    return Result.create(result == null ? PsiElement.EMPTY_ARRAY : result, PsiModificationTracker.MODIFICATION_COUNT);
  }

  private static XmlTag[] extractXpointer(@NotNull XmlTag rootTag, @Nullable final String xpointer) {
    if (xpointer != null) {
      Matcher matcher = JDOMUtil.XPOINTER_PATTERN.matcher(xpointer);
      if (matcher.matches()) {
        String pointer = matcher.group(1);
        matcher = JDOMUtil.CHILDREN_PATTERN.matcher(pointer);
        if (matcher.matches() && matcher.group(1).equals(rootTag.getName())) {
          XmlTag[] tags = rootTag.getSubTags();
          String subTagName = matcher.group(2);
          if (subTagName == null) return tags;

          XmlTag subTag = ContainerUtil.find(tags, t -> subTagName.substring(1).equals(t.getName()));
          return subTag == null ? XmlTag.EMPTY : subTag.getSubTags();
        }
      }
    }

    return new XmlTag[]{rootTag};
  }

  private static PsiElement @Nullable [] computeInclusion(final XmlTag xincludeTag) {
    final XmlFile included = XmlIncludeHandler.resolveXIncludeFile(xincludeTag);
    if (IdempotenceChecker.isLoggingEnabled()) {
      IdempotenceChecker.logTrace("InclusionProvider resolved file=" + included);
    }
    final XmlDocument document = included != null ? included.getDocument() : null;
    final XmlTag rootTag = document != null ? document.getRootTag() : null;
    if (rootTag != null) {
      final String xpointer = xincludeTag.getAttributeValue("xpointer", XmlPsiUtil.XINCLUDE_URI);
      final XmlTag[] includeTag = extractXpointer(rootTag, xpointer);
      if (IdempotenceChecker.isLoggingEnabled()) {
        IdempotenceChecker.logTrace("InclusionProvider found " + includeTag.length + " tags by " + xpointer);
      }
      PsiElement[] result = new PsiElement[includeTag.length];
      for (int i = 0; i < includeTag.length; i++) {
        result[i] = new IncludedXmlTag(includeTag[i], xincludeTag.getParentTag());
      }
      return result;
    }

    return null;
  }

}
