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
package com.jetbrains.python.codeInsight.intentions;

import com.google.common.collect.Lists;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.jetbrains.python.codeInsight.intentions.SpecifyTypeInPy3AnnotationsIntention.*;
import static com.jetbrains.python.codeInsight.intentions.TypeIntention.getMultiCallable;
import static com.jetbrains.python.codeInsight.intentions.TypeIntention.resolvesToFunction;

/**
 * @author traff
 */
public class PyAnnotateTypesIntention extends PyBaseIntentionAction {
  
  public PyAnnotateTypesIntention() {
    setText(PyBundle.message("INTN.annotate.types"));
  }

  @NotNull
  public String getFamilyName() {
    return PyBundle.message("INTN.annotate.types");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile) || file instanceof PyDocstringFile) return false;

    updateText();

    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    if (elementAt == null) return false;

    if (resolvesToFunction(elementAt, input -> true)) {
      updateText();
      return true;
    }
    return false;
  }

  @Override
  public void doInvoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
    getMultiCallable(elementAt).forEach(callable -> annotateTypes(editor, callable));
  }

  public static void annotateTypes(Editor editor, PyCallable callable) {
    if (isPy3k(callable.getContainingFile())) {
      generatePy3kTypeAnnotations(callable.getProject(), editor, callable);
    }
    else {
      if (callable instanceof PyFunction) {
        generateTypeCommentAnnotations(callable.getProject(), (PyFunction)callable);
      }
    }
  }

  private static void generateTypeCommentAnnotations(Project project, PyFunction function) {

    StringBuilder replacementTextBuilder = new StringBuilder("# type: (");

    PyParameter[] params = function.getParameterList().getParameters();

    List<Pair<Integer, String>> templates = Lists.newArrayList();

    for (int i = 0; i < params.length; i++) {
      if (!params[i].isSelf()) {
        String type = parameterType(params[i]);

        templates.add(Pair.create(replacementTextBuilder.length(), type));

        replacementTextBuilder.append(type);

        if (i < params.length - 1) {
          replacementTextBuilder.append(", ");
        }
      }
    }

    replacementTextBuilder.append(") -> ");

    String returnType = returnType(function);
    templates.add(Pair.create(replacementTextBuilder.length(), returnType));

    replacementTextBuilder.append(returnType);

    final PyStatementList statements = function.getStatementList();
    final String indentation = PyIndentUtil.getElementIndent(statements);
    replacementTextBuilder.insert(0, indentation);
    replacementTextBuilder.insert(0, "\n");


    final PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
    final Document document = manager.getDocument(function.getContainingFile());

    if (document != null) {
      final PsiElement beforeStatements = statements.getPrevSibling();
      int offset = beforeStatements.getTextRange().getStartOffset();
      if (":".equals(beforeStatements.getText())) {
        offset += 1;
      }
      try {
        document.insertString(offset, replacementTextBuilder.toString());
      }
      finally {
        manager.commitDocument(document);
      }


      PsiElement element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);

      while (element != null && !element.getText().contains(replacementTextBuilder.toString())) {
        element = element.getParent();
      }
      
      if (element != null) {
        final TemplateBuilder builder =
          TemplateBuilderFactory.getInstance().createTemplateBuilder(element);

        for (Pair<Integer, String> template : templates) {
          builder.replaceRange(TextRange.from(
            offset - element.getTextRange().getStartOffset() + replacementTextBuilder.toString().indexOf('#') + template.first,
            template.second.length()), template.second);
        }

        startTemplate(project, element, builder);
      }
    }
  }

  private static void startTemplate(Project project, PsiElement callable, TemplateBuilder builder) {
    final Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();

    int offset = callable.getTextRange().getStartOffset();

    final OpenFileDescriptor descriptor = new OpenFileDescriptor(
      project,
      callable.getContainingFile().getVirtualFile(),
      offset
    );
    final Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    if (targetEditor != null) {
      targetEditor.getCaretModel().moveToOffset(offset);
      TemplateManager.getInstance(project).startTemplate(targetEditor, template);
    }
  }

  private static boolean isPy3k(PsiFile file) {
    return !LanguageLevel.forElement(file).isPython2();
  }

  private static void generatePy3kTypeAnnotations(@NotNull Project project, Editor editor, PyCallable callable) {
    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(callable);

    if (callable instanceof PyFunction) {
      PyExpression returnType = annotateReturnType(project, (PyFunction) callable, false);

      if (returnType != null) {
        builder.replaceElement(returnType, returnType.getText());
      }
    }

    if (callable instanceof PyFunction) {
      PyFunction function = (PyFunction)callable;
      PyParameter[] params = function.getParameterList().getParameters();

      for (int i = params.length - 1; i >= 0; i--) {
        if (params[i] instanceof PyNamedParameter && !params[i].isSelf()) {
          params[i] = annotateParameter(project, editor, (PyNamedParameter)params[i], false);
        }
      }


      for (int i = params.length - 1; i >= 0; i--) {
        if (params[i] instanceof PyNamedParameter) {
          if (!params[i].isSelf()) {
            params[i] = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(params[i]);
            PyAnnotation annotation = ((PyNamedParameter)params[i]).getAnnotation();
            if (annotation != null) {
              PyExpression annotationValue = annotation.getValue();
              if (annotationValue != null) {
                builder.replaceElement(annotationValue, annotationValue.getText());
              }
            }
          }
        }
      }
    }
    if (callable != null) {
      startTemplate(project, callable, builder);
    }
  }

  protected void updateText() {
    setText(PyBundle.message("INTN.annotate.types"));
  }
}