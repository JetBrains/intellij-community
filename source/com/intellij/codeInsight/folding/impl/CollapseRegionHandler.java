package com.intellij.codeInsight.folding.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;

public class CollapseRegionHandler implements CodeInsightActionHandler {
  public void invoke(Project project, final Editor editor, PsiFile file){
    CodeFoldingManager foldingManager = CodeFoldingManager.getInstance(project);
    foldingManager.updateFoldRegions(editor);

    final int line = editor.getCaretModel().getLogicalPosition().line;

    Runnable processor = new Runnable() {
      public void run() {
        FoldRegion region = FoldingUtil.findFoldRegionStartingAtLine(editor, line);
        if (region != null && region.isExpanded()){
          region.setExpanded(false);
        }
        else{
          int offset = editor.getCaretModel().getOffset();
          FoldRegion[] regions = FoldingUtil.getFoldRegionsAtOffset(editor, offset);
          for(int i = 0; i < regions.length; i++){
            region = regions[i];
            if (region.isExpanded()){
              region.setExpanded(false);
              break;
            }
          }
        }
      }
    };
    editor.getFoldingModel().runBatchFoldingOperation(processor);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
