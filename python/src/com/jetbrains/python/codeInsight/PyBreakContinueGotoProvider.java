/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.codeInsight;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandlerBase;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * Provides reaction on ctrl+click for {@code break} and {@code continue} statements.
 * @author dcheryasov
 */
public class PyBreakContinueGotoProvider extends GotoDeclarationHandlerBase {
  @Override
  public PsiElement getGotoDeclarationTarget(@Nullable PsiElement source, Editor editor) {
    if (source != null && source.getLanguage() instanceof PythonLanguage) {
      final PyLoopStatement loop = PsiTreeUtil.getParentOfType(source, PyLoopStatement.class, false, PyFunction.class, PyClass.class);
      if (loop != null) {
        ASTNode node = source.getNode();
        if (node != null) {
          IElementType node_type = node.getElementType();
          if (node_type == PyTokenTypes.CONTINUE_KEYWORD) {
            return loop;
          }
          if (node_type == PyTokenTypes.BREAK_KEYWORD) {
            PsiElement outer_element = loop;
            PsiElement after_cycle;
            while (true) {
              after_cycle = outer_element.getNextSibling();
              if (after_cycle != null) {
                if (after_cycle instanceof PsiWhiteSpace) {
                  after_cycle = after_cycle.getNextSibling();
                }
                if (after_cycle instanceof PyStatement) return after_cycle;
              }
              outer_element = outer_element.getParent();
              if (PyUtil.instanceOf(outer_element, PsiFile.class, PyFunction.class, PyClass.class)) {
                break;
              }
            }
            // cycle is the last statement in the flow of execution. its last element is our best bet.
            return PsiTreeUtil.getDeepestLast(loop);
          }
        }
      }
    }

    return null;
  }
}
