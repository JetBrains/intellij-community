// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.binding;

import com.intellij.lang.ASTNode;
import com.intellij.lang.folding.FoldingBuilderEx;
import com.intellij.lang.folding.FoldingDescriptor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;


public class GeneratedCodeFoldingBuilder extends FoldingBuilderEx {
  @Override
  public FoldingDescriptor @NotNull [] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    MyFoldingVisitor visitor = new MyFoldingVisitor();
    root.accept(visitor);
    return visitor.myFoldingData.toArray(FoldingDescriptor.EMPTY_ARRAY);
  }

  @Override
  public String getPlaceholderText(@NotNull ASTNode node) {
    return UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text");
  }

  @Override
  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return true;
  }

  private static boolean isGeneratedUIInitializer(PsiClassInitializer initializer) {
    PsiCodeBlock body = initializer.getBody();
    if (body.getStatementCount() != 1) return false;
    PsiStatement statement = body.getStatements()[0];
    if (!(statement instanceof PsiExpressionStatement) ||
        !(((PsiExpressionStatement)statement).getExpression() instanceof PsiMethodCallExpression)) {
      return false;
    }

    PsiMethodCallExpression call = (PsiMethodCallExpression)((PsiExpressionStatement)statement).getExpression();
    return AsmCodeGenerator.SETUP_METHOD_NAME.equals(call.getMethodExpression().getReferenceName());
  }

  private static class MyFoldingVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiElement myLastElement;
    private final List<FoldingDescriptor> myFoldingData = new ArrayList<>();

    @Override
      public void visitMethod(@NotNull PsiMethod method) {
      String methodName = method.getName();
      if (AsmCodeGenerator.SETUP_METHOD_NAME.equals(methodName) ||
          AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(methodName) ||
          AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD.equals(methodName) ||
          AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD.equals(methodName) ||
          AsmCodeGenerator.GET_FONT_METHOD_NAME.equals(methodName) ||
          AsmCodeGenerator.GET_MESSAGE_FROM_BUNDLE.equals(methodName)) {

        addFoldingData(method);
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      if (AsmCodeGenerator.CACHED_GET_BUNDLE_METHOD.equals(field.getName())) {
        addFoldingData(field);
      }
    }

    @Override
    public void visitClassInitializer(@NotNull PsiClassInitializer initializer) {
      if (isGeneratedUIInitializer(initializer)) {
        addFoldingData(initializer);
      }
    }

    private void addFoldingData(final PsiElement element) {
      PsiElement prevSibling = PsiTreeUtil.skipWhitespacesBackward(element);
      synchronized (myFoldingData) {
        if (myLastElement == null || prevSibling != myLastElement) {
          myFoldingData.add(new FoldingDescriptor(element, element.getTextRange()));
        }
        else {
          FoldingDescriptor lastDescriptor = myFoldingData.get(myFoldingData.size()-1);
          final TextRange range = new TextRange(lastDescriptor.getRange().getStartOffset(), element.getTextRange().getEndOffset());
          myFoldingData.set(myFoldingData.size()-1, new FoldingDescriptor(lastDescriptor.getElement(), range));
        }
      }
      myLastElement =  element;
    }
  }
}
