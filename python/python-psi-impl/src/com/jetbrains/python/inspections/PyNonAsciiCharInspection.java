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
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;

/**
 * User : catherine
 */
public class PyNonAsciiCharInspection extends PyInspection {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private class Visitor extends PyInspectionVisitor {
    Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
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

        CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder();
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
            registerProblem(node, "Non-ASCII character " + c + " in file, but no encoding declared",
                            new AddEncodingQuickFix(myDefaultEncoding, myEncodingFormatIndex));
          }
        }
      }
    }

    @Override
    public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
      checkString(node, node.getText());
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      checkString(node, node.getText());
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
      checkString(node, node.getText());
    }
  }

  public String myDefaultEncoding = "utf-8";
  public int myEncodingFormatIndex = 0;

  @Override
  public JComponent createOptionsPanel() {
    return PythonUiService.getInstance()
      .createEncodingsOptionsPanel(PyEncodingUtil.POSSIBLE_ENCODINGS, myDefaultEncoding, PyEncodingUtil.ENCODING_FORMAT,
                                   myEncodingFormatIndex,
                                   encoding -> {
                                     myDefaultEncoding = encoding;
                                   },
                                   formatIndex -> {
                                     myEncodingFormatIndex = formatIndex;
                                   });
  }
}
