/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.binding;

import com.intellij.codeHighlighting.TextEditorHighlightingPass;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class GeneratedCodeFoldingPass extends TextEditorHighlightingPass {
  private final PsiFile myPsiFile;
  private final Editor myEditor;

  private List<TextRange> myFoldingData;

  protected GeneratedCodeFoldingPass(final PsiFile psiFile, final Editor editor) {
    super(editor.getDocument());
    myPsiFile = psiFile;
    myEditor = editor;
  }

  public void doCollectInformation(final ProgressIndicator progress) {
    myFoldingData = new ArrayList<TextRange>();
    myPsiFile.accept(new MyFoldingVisitor(progress));
  }

  public void doApplyInformationToEditor() {
    myEditor.getFoldingModel().runBatchFoldingOperation(new Runnable() {
      public void run() {
        FoldingModel foldingModel = myEditor.getFoldingModel();
        for(TextRange foldingData: myFoldingData) {
          final int startOffset = foldingData.getStartOffset();
          final int endOffset = foldingData.getEndOffset();

          boolean generatedCodeUnfolded = false;
          FoldRegion[] regions = foldingModel.getAllFoldRegions();
          for(FoldRegion region: regions) {
            if (region.getPlaceholderText().equals(UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text")) &&
              region.isExpanded()) {
              generatedCodeUnfolded = true;
            }
            if (region.getStartOffset() >= startOffset && region.getEndOffset() <= endOffset) {
              foldingModel.removeFoldRegion(region);
            }
          }

          final FoldRegion region =
            foldingModel.addFoldRegion(startOffset, endOffset, UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text"));
          if (region != null && !generatedCodeUnfolded) {
            region.setExpanded(false);
          }
        }
      }
    });
  }

  public int getPassId() {
    return 7890;
  }

  private static boolean isGeneratedUIInitializer(PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    if (body.getStatements().length != 1) return false;
    PsiStatement statement = body.getStatements()[0];
    if (!(statement instanceof PsiExpressionStatement) ||
        !(((PsiExpressionStatement)statement).getExpression() instanceof PsiMethodCallExpression)) {
      return false;
    }

    PsiMethodCallExpression call = (PsiMethodCallExpression)((PsiExpressionStatement)statement).getExpression();
    return AsmCodeGenerator.SETUP_METHOD_NAME.equals(call.getMethodExpression().getReferenceName());
  }

  private class MyFoldingVisitor extends PsiRecursiveElementVisitor {
    private final ProgressIndicator myProgress;
    private PsiElement myLastElement;

    public MyFoldingVisitor(final ProgressIndicator progress) {
      myProgress = progress;
    }

    @Override
      public void visitMethod(PsiMethod method) {
      myProgress.checkCanceled();
      if (AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD.equals(method.getName())) {
        addFoldingData(method);
      }
    }

    @Override
      public void visitClassInitializer(PsiClassInitializer initializer) {
      myProgress.checkCanceled();
      if (isGeneratedUIInitializer(initializer)) {
        addFoldingData(initializer);
      }
    }

    private void addFoldingData(final PsiElement element) {
      PsiElement prevSibling = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace.class);
      if (myLastElement == null || prevSibling != myLastElement) {
        myFoldingData.add(element.getTextRange());
      }
      else {
        TextRange lastRange = myFoldingData.get(myFoldingData.size()-1);
        myFoldingData.set(myFoldingData.size()-1, new TextRange(lastRange.getStartOffset(), element.getTextRange().getEndOffset()));
      }
      myLastElement =  element;
    }
  }
}
