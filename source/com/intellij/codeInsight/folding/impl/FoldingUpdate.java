package com.intellij.codeInsight.folding.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.HashMap;

import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

class FoldingUpdate {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.folding.impl.FoldingUpdate");

  private static final Key<Object> LAST_UPDATE_STAMP_KEY = Key.create("LAST_UPDATE_STAMP_KEY");

  public static void updateFoldRegions(Document document, PsiFile file) {
    Editor[] editors = EditorFactory.getInstance().getEditors(document, file.getProject());
    for(int i = 0; i < editors.length; i++){
      Runnable runnable = updateFoldRegions(editors[i], file, false);
      if (runnable != null){
        runnable.run();
      }
    }
  }

  public static Runnable updateFoldRegions(final Editor editor, PsiElement file, boolean applyDefaultState) {
    ApplicationManager.getApplication().assertReadAccessAllowed();

    final Project project = file.getProject();
    Document document = editor.getDocument();
    LOG.assertTrue(!PsiDocumentManager.getInstance(project).isUncommited(document));

    final long timeStamp = document.getModificationStamp();
    Object lastTimeStamp = editor.getUserData(LAST_UPDATE_STAMP_KEY);
    if (lastTimeStamp instanceof Long && ((Long)lastTimeStamp).longValue() == timeStamp) return null;

    if (file instanceof PsiCompiledElement){
      file = ((PsiCompiledElement)file).getMirror();
    }

    TreeMap elementsToFoldMap = FoldingPolicy.getElementsToFold(file, document);

    final Runnable operation = new UpdateFoldRegionsOperation(editor, elementsToFoldMap, applyDefaultState);
    return new Runnable() {
      public void run() {
        editor.getFoldingModel().runBatchFoldingOperation(operation);
        editor.putUserData(LAST_UPDATE_STAMP_KEY, new Long(timeStamp));
      }
    };
  }

  private static class UpdateFoldRegionsOperation implements Runnable {
    private final Editor myEditor;
    private final boolean myApplyDefaultState;
    private final TreeMap myElementsToFoldMap;

    public UpdateFoldRegionsOperation(Editor editor, TreeMap elementsToFoldMap, boolean applyDefaultState) {
      myEditor = editor;
      myApplyDefaultState = applyDefaultState;
      myElementsToFoldMap = elementsToFoldMap;
    }

    public void run() {
      EditorFoldingInfo info = EditorFoldingInfo.get(myEditor);
      FoldingModelEx foldingModel = (FoldingModelEx)myEditor.getFoldingModel();
      FoldRegion[] foldRegions = foldingModel.getAllFoldRegions();
      HashMap<TextRange,Boolean> rangeToExpandStatusMap = new HashMap<TextRange, Boolean>();

      for (int i = 0; i < foldRegions.length; i++) {
        FoldRegion region = foldRegions[i];
        PsiElement element = info.getPsiElement(region);
        if (element != null && myElementsToFoldMap.containsKey(element)){
          TextRange range = (TextRange)myElementsToFoldMap.get(element);
          boolean toRemove = !region.isValid() ||
                  region.getStartOffset() != range.getStartOffset() ||
                  region.getEndOffset() != range.getEndOffset();
          if (toRemove){
            boolean isExpanded = region.isExpanded();
            rangeToExpandStatusMap.put(range, isExpanded ? Boolean.TRUE : Boolean.FALSE);
            foldingModel.removeFoldRegion(region);
            info.removeRegion(region);
          }
          else {
            myElementsToFoldMap.remove(element);
          }
        }
        else{
          if (region.isValid() && info.isLightRegion(region)){
            boolean isExpanded = region.isExpanded();
            rangeToExpandStatusMap.put(new TextRange(region.getStartOffset(), region.getEndOffset()),
              isExpanded ? Boolean.TRUE : Boolean.FALSE);
          } else {
            foldingModel.removeFoldRegion(region);
            info.removeRegion(region);
          }
        }
      }

      for(Iterator<Map.Entry> iterator = myElementsToFoldMap.entrySet().iterator(); iterator.hasNext();){
        ProgressManager.getInstance().checkCanceled();
        
        Map.Entry entry = iterator.next();
        PsiElement element = (PsiElement)entry.getKey();
        TextRange range = (TextRange)entry.getValue();
        String foldingText = FoldingPolicy.getFoldingText(element);
        FoldRegion region = foldingModel.addFoldRegion(range.getStartOffset(), range.getEndOffset(), foldingText);
        if (region == null) continue;
        //region.setGreedyToRight(true); //?
        info.addRegion(region, element);

        if (myApplyDefaultState){
          region.setExpanded(!FoldingPolicy.isCollapseByDefault(element));
        }
        else{
          Boolean status = rangeToExpandStatusMap.get(range);
          if (status != null){
            region.setExpanded(status.booleanValue());
          }
        }
      }
    }
  }
}
