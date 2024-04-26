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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;

import static com.intellij.codeInspection.options.OptPane.pane;
import static com.jetbrains.python.inspections.PyMandatoryEncodingInspection.defaultEncodingDropDown;
import static com.jetbrains.python.inspections.PyMandatoryEncodingInspection.encodingFormatDropDown;

/**
 * User : catherine
 */
public final class PyNonAsciiCharInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  private class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull TypeEvalContext context) {
      super(holder, context);
    }
    @Override
    public void visitComment(@NotNull PsiComment node) {
      checkString(node, node.getText());
    }

    private void checkString(PsiElement node, String value) {
      if (LanguageLevel.forElement(node).isPython2()) {
        PsiFile file = node.getContainingFile(); // can't cache this in the instance, alas
        if (file == null) return;
        final String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(file);

        boolean hasNonAscii = false;

        CharsetEncoder asciiEncoder = StandardCharsets.US_ASCII.newEncoder();
        int length = value.length();
        char c = 0;
        for (int i = 0; i < length; ++i) {
          c = value.charAt(i);
          if (!asciiEncoder.canEncode(c)) {
            hasNonAscii = true;
            break;
          }
        }

        if (hasNonAscii) {
          if (charsetString == null) {
            registerProblem(node, PyPsiBundle.message("INSP.non.ascii.char.non.ascii.character.in.file.but.no.encoding.declared", c),
                            new AddEncodingQuickFix(myDefaultEncoding, myEncodingFormatIndex));
          }
        }
      }
    }

    @Override
    public void visitPyStringLiteralExpression(@NotNull PyStringLiteralExpression node) {
      checkString(node, node.getText());
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      checkString(node, node.getText());
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      checkString(node, node.getText());
    }
  }

  public @NlsSafe String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(defaultEncodingDropDown(), encodingFormatDropDown());
  }
}
