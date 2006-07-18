package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.impl.cache.impl.idCache.IdTableBuilding;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

public class AllWordsGetter implements ContextGetter {
  public Object[] get(final PsiElement context, final CompletionContext completionContext) {
    if (completionContext.getPrefix().length() == 0) {
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    final char [] chars = context.getContainingFile().getText().toCharArray();
    final List<String> objs = new ArrayList<String>();
    IdTableBuilding.scanWords(new IdTableBuilding.ScanWordProcessor(){
      public void run(final char[] chars, final int start, final int end) {
        final int len = end - start;
        if (completionContext == null || start > completionContext.offset || completionContext.offset >= end) {
          objs.add(String.valueOf(chars, start, len));
        }
      }
    }, chars, 0, chars.length);
    return objs.toArray();
  }
}
