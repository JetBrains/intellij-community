/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

/**
 * @author yole
 */
public class GeneratedCodeFoldingBuilder extends FoldingBuilderEx {
  @NotNull
  public FoldingDescriptor[] buildFoldRegions(@NotNull PsiElement root, @NotNull Document document, boolean quick) {
    MyFoldingVisitor visitor = new MyFoldingVisitor();
    root.accept(visitor);
    return visitor.myFoldingData.toArray(new FoldingDescriptor[visitor.myFoldingData.size()]);
  }

  public String getPlaceholderText(@NotNull ASTNode node) {
    return UIDesignerBundle.message("uidesigner.generated.code.folding.placeholder.text");
  }

  public boolean isCollapsedByDefault(@NotNull ASTNode node) {
    return true;
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

  private static class MyFoldingVisitor extends JavaRecursiveElementWalkingVisitor {
    private PsiElement myLastElement;
    private final List<FoldingDescriptor> myFoldingData = new ArrayList<>();

    @Override
      public void visitMethod(PsiMethod method) {
      if (AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.GET_ROOT_COMPONENT_METHOD_NAME.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_BUTTON_TEXT_METHOD.equals(method.getName()) ||
          AsmCodeGenerator.LOAD_LABEL_TEXT_METHOD.equals(method.getName())) {
        addFoldingData(method);
      }
    }

    @Override
    public void visitClassInitializer(PsiClassInitializer initializer) {
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
