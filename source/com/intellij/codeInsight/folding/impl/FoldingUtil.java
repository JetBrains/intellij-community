package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;

import java.util.*;

class FoldingUtil {

  public static FoldRegion findFoldRegion(Editor editor, PsiElement element) {
    TextRange range = FoldingPolicy.getRangeToFold(element);
    if (range == null) return null;
    return findFoldRegion(editor, range.getStartOffset(), range.getEndOffset());

  }

  public static FoldRegion findFoldRegion(Editor editor, int startOffset, int endOffset) {
    FoldRegion[] foldRegions = ((FoldingModelEx)editor.getFoldingModel()).getAllFoldRegionsIncludingInvalid();
    for (int i = 0; i < foldRegions.length; i++) {
      FoldRegion region = foldRegions[i];
      if (region.isValid() &&
        region.getStartOffset() == startOffset
        && region.getEndOffset() == endOffset){
        return region;
      }
    }

    return null;
  }

  public static FoldRegion findFoldRegionStartingAtLine(Editor editor, int line){
    FoldRegion[] regions = editor.getFoldingModel().getAllFoldRegions();
    FoldRegion result = null;
    for(int i = 0; i < regions.length; i++){
      FoldRegion region = regions[i];
      if (region.getDocument().getLineNumber(region.getStartOffset()) == line) {
        if (result != null) return null;
        result = region;
      }
    }
    return result;
  }

  public static FoldRegion[] getFoldRegionsAtOffset(Editor editor, int offset){
    ArrayList<FoldRegion> list = new ArrayList<FoldRegion>();
    FoldRegion[] allRegions = editor.getFoldingModel().getAllFoldRegions();
    for(int i = 0; i < allRegions.length; i++){
      FoldRegion region = allRegions[i];
      if (region.getStartOffset() <= offset && offset <= region.getEndOffset()){
        list.add(region);
      }
    }

    FoldRegion[] regions = list.toArray(new FoldRegion[list.size()]);
    Arrays.sort(
      regions,
      new Comparator() {
        public int compare(Object o1, Object o2) {
          FoldRegion region1 = (FoldRegion)o1;
          FoldRegion region2 = (FoldRegion)o2;
          return region2.getStartOffset() - region1.getStartOffset();
        }
      }
    );

    return regions;
  }
}