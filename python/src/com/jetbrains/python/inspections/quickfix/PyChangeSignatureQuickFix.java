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
package com.jetbrains.python.inspections.quickfix;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureDialog;
import com.jetbrains.python.refactoring.changeSignature.PyMethodDescriptor;
import com.jetbrains.python.refactoring.changeSignature.PyParameterInfo;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PyChangeSignatureQuickFix extends LocalQuickFixOnPsiElement {

  @NotNull private final List<Pair<Integer, PyParameterInfo>> myExtraParameters;

  @NotNull
  public static PyChangeSignatureQuickFix forMismatchingMethods(@NotNull PyFunction function, @NotNull PyFunction complementary) {
    final int paramLength = function.getParameterList().getParameters().length;
    final int complementaryParamLength = complementary.getParameterList().getParameters().length;
    if (complementaryParamLength > paramLength) {
      return new PyChangeSignatureQuickFix(function,
                                           Collections.singletonList(Pair.create(paramLength - 1,
                                                                                 new PyParameterInfo(-1, "**kwargs", "", false))));
    }
    return new PyChangeSignatureQuickFix(function, Collections.emptyList());
  }


  public PyChangeSignatureQuickFix(@NotNull PyFunction function, @NotNull List<Pair<Integer, PyParameterInfo>> extraParameters) {
    super(function);
    myExtraParameters = ContainerUtil.sorted(extraParameters, Comparator.comparingInt(p -> p.getFirst()));
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("QFIX.NAME.change.signature");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
    final PyChangeSignatureDialog dialog = new PyChangeSignatureDialog(project, createMethodDescriptor((PyFunction)startElement));
    dialog.show();
  }

  @NotNull
  private PyMethodDescriptor createMethodDescriptor(final PyFunction function) {
    return new PyMethodDescriptor(function) {
        @Override
        public List<PyParameterInfo> getParameters() {
          final List<PyParameterInfo> result = new ArrayList<>();
          final List<PyParameterInfo> originalParams = super.getParameters();
          final PeekingIterator<Pair<Integer, PyParameterInfo>> extra = Iterators.peekingIterator(myExtraParameters.iterator());
          while (extra.hasNext() && extra.peek().getFirst() < 0) {
            result.add(extra.next().getSecond());
          }
          for (int i = 0; i < originalParams.size(); i++) {
            result.add(originalParams.get(i));
            while (extra.hasNext() && extra.peek().getFirst() == i) {
              result.add(extra.next().getSecond());
            }
          }
          return result;
        }
      };
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
