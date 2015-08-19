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
package com.jetbrains.python.documentation;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.documentation.docstrings.DocStringProvider;
import com.jetbrains.python.documentation.docstrings.TagBasedDocStringBuilder;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author traff
 */
public class PyDocstringGenerator {

  @NotNull
  private PyDocStringOwner myDocStringOwner;
  @Nullable
  private PyFunction myFunction;

  private final List<DocstringParam> myParams = Lists.newArrayList();
  private final Project myProject;
  private PyStringLiteralExpression myDocStringExpression;

  private final PsiFile myFile;
  private boolean myGenerateReturn;

  private final StructuredDocString myStructuredDocString;
  private final DocStringFormat myDocStringFormat;

  public PyDocstringGenerator(@NotNull PyDocStringOwner docStringOwner) {
    myDocStringOwner = docStringOwner;
    if (docStringOwner instanceof PyFunction) {
      myFunction = (PyFunction)docStringOwner;
    }
    myProject = myDocStringOwner.getProject();
    myFile = myDocStringOwner.getContainingFile();
    myDocStringFormat = DocStringUtil.getDocStringFormat(docStringOwner);
    final DocStringProvider provider = myDocStringFormat.getProvider();
    final PyStringLiteralExpression docStringExpression = myDocStringOwner.getDocStringExpression();
    if (docStringExpression != null) {
      myStructuredDocString = provider.parseDocString(docStringExpression);
    }
    else {
      myStructuredDocString = null;
    }
  }

  @NotNull
  public PyDocstringGenerator withParam(@NotNull String name) {
    return withParamTypedByName(name, null);
  }

  @NotNull
  public PyDocstringGenerator withParamTypedByName(@NotNull String name, @Nullable String type) {
    myParams.add(new DocstringParam(name, type, false));
    return this;
  }

  @NotNull
  public PyDocstringGenerator withReturnValue(@Nullable String type) {
    myParams.add(new DocstringParam("", type, true));
    return this;
  }

  @NotNull
  public PyDocstringGenerator addReturn() {
    myGenerateReturn = true;
    return this;
  }

  public static String generateRaiseOrReturn(@NotNull PyFunction element, String offset, String prefix, boolean checkReturn) {
    final StringBuilder builder = new StringBuilder();
    if (checkReturn) {
      final RaiseVisitor visitor = new RaiseVisitor();
      final PyStatementList statementList = element.getStatementList();
      statementList.accept(visitor);
      if (visitor.myHasReturn) {
        builder.append(prefix).append("return:").append(offset);
        if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
          builder.append(prefix).append("rtype:").append(offset);
        }
      }
      if (visitor.myHasRaise) {
        builder.append(prefix).append("raise");
        if (visitor.myRaiseTarget != null) {
          builder.append(" ").append(visitor.getRaiseTargetText());
        }
        builder.append(":").append(offset);
      }
    }
    else {
      builder.append(prefix).append("return:").append(offset);
      if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
        builder.append(prefix).append("rtype:").append(offset);
      }
    }
    return builder.toString();
  }

  private PsiFile getFile() {
    return myFile;
  }

  public void startTemplate() {
    assert myDocStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(myDocStringExpression);

    if (myParams.size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    final DocstringParam paramToEdit = getParamToEdit();
    final String paramName = paramToEdit.getName();
    final String stringContent = myDocStringExpression.getStringValue();
    final StructuredDocString parsed = DocStringUtil.parse(stringContent);
    if (parsed == null) {
      return;
    }
    Substring substring;
    if (paramToEdit.isReturnValue()) {
      substring = parsed.getReturnTypeSubstring();
    }
    else {
      substring = parsed.getParamTypeSubstring(paramName);
    }
    if (substring == null) {
      return;
    }
    builder.replaceRange(substring.getTextRange().shiftRight(myDocStringExpression.getStringValueTextRange().getStartOffset()),
                         getDefaultType());
    Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
    final VirtualFile virtualFile = myFile.getVirtualFile();
    if (virtualFile == null) return;
    OpenFileDescriptor descriptor = new OpenFileDescriptor(myProject, virtualFile, myDocStringExpression.getTextOffset());
    Editor targetEditor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
    if (targetEditor != null) {
      TemplateManager.getInstance(myProject).startTemplate(targetEditor, template);
    }
  }

  private String getDefaultType() {
    DocstringParam param = getParamToEdit();
    if (StringUtil.isEmpty(param.getType())) {
      return PyNames.OBJECT;
    }
    else {
      return param.getType();
    }
  }

  @NotNull
  private String buildDocString() {
    if (myStructuredDocString != null) {
      return updateDocString();
    }
    else {
      return createDocString();
    }
  }

  @NotNull
  private String createDocString() {
    if (myDocStringFormat == DocStringFormat.EPYTEXT || myDocStringFormat == DocStringFormat.REST) {
      final TagBasedDocStringBuilder builder = new TagBasedDocStringBuilder(myDocStringFormat == DocStringFormat.EPYTEXT ? "@" : ":");
      builder.addLine("");
      String indentation = "";
      if (myDocStringOwner instanceof PyStatementListContainer) {
        indentation = PyIndentUtil.getElementIndent(((PyStatementListContainer)myDocStringOwner).getStatementList());
      }
      boolean addedReturn = false;
      for (DocstringParam param : myParams) {
        if (param.isReturnValue()) {
          if (param.getType() != null) {
            builder.addReturnValueType(param.getType());
          }
          else {
            builder.addReturnValueDescription("");
          }
          addedReturn = true;
        }
        else {
          if (param.getType() != null) {
            builder.addParameterType(param.getName(), param.getType());
          }
          else {
            builder.addParameterDescription(param.getName(), "");
          }
        }
      }
      if (myGenerateReturn && myDocStringOwner instanceof PyFunction) {
        final RaiseVisitor visitor = new RaiseVisitor();
        final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
        statementList.accept(visitor);
        if (!addedReturn && visitor.myHasReturn) {
          builder.addReturnValueDescription("");
        }
        if (visitor.myHasRaise) {
          builder.addExceptionDescription(visitor.getRaiseTargetText(), "");
        }
      }

      if (builder.getLines().size() > 1) {
        return "\"\"\"\n" + builder.buildContent(indentation, true) + '\n' + indentation + "\"\"\"";
      }
      else {
        return "\"\"\"" + builder.buildContent(indentation, false) + "\"\"\"";
      }
    }
    else {
      return "";
    }
  }

  @NotNull
  private String updateDocString() {
    return "";
  }

  public boolean haveParametersToAdd() {
    return !collectParametersToAdd().isEmpty();
  }

  @NotNull
  private Collection<DocstringParam> collectParametersToAdd() {
    return Collections2.filter(myParams, new Predicate<DocstringParam>() {
      @Override
      public boolean apply(DocstringParam param) {
        if (param.isReturnValue()) {
          return myStructuredDocString.getReturnTypeSubstring() == null;
        }
        else {
          return myStructuredDocString.getParamTypeSubstring(param.myName) == null;
        }
      }
    });
  }

  public String docStringAsText() {
    return buildDocString();
  }

  private DocstringParam getParamToEdit() {
    if (myParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myParams.get(0);
  }

  public void buildAndInsert() {
    final String replacement = buildDocString();

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(myProject);
    // update existing docstring
    if (myStructuredDocString != null) {
      PyExpression str = elementGenerator.createDocstring(replacement).getExpression();
      myDocStringExpression.replace(str);
      myDocStringOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myDocStringOwner);
      if (myDocStringOwner instanceof PyFunction) {
        myFunction = (PyFunction)myDocStringOwner;
      }
    }
    // create brand new docstring
    else {
      if (myFunction == null) {
        throw new IllegalStateException("Should be a function");
      }
      final PyStatementList statements = myFunction.getStatementList();
      final String indentation = PyIndentUtil.getExpectedElementIndent(statements);
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(getFile());

      if (document != null) {
        if (PyUtil.onSameLine(statements, myFunction) || statements.getStatements().length == 0) {
          PyFunction func = elementGenerator.createFromText(LanguageLevel.forElement(myFunction),
                                                            PyFunction.class,
                                                            "def " + myFunction.getName() + myFunction.getParameterList().getText() + ":\n" +
                                                            indentation + replacement + "\n" +
                                                            indentation + statements.getText());

          myFunction = (PyFunction)myFunction.replace(func);
        }
        else {
          PyExpressionStatement str = elementGenerator.createDocstring(replacement);
          statements.addBefore(str, statements.getStatements()[0]);
        }
      }

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myFunction);
      myDocStringExpression = myFunction.getDocStringExpression();
    }
  }

  public static class DocstringParam {
    private final String myName;
    private final String myType;
    private final boolean myReturnValue;

    private DocstringParam(@NotNull String name, @Nullable String type, boolean isReturn) {
      myName = name;
      myType = type;
      myReturnValue = isReturn;
    }

    @NotNull
    public String getName() {
      return myName;
    }

    @Nullable
    public String getType() {
      return myType;
    }

    public boolean isReturnValue() {
      return myReturnValue;
    }
  }

  private static class RaiseVisitor extends PyRecursiveElementVisitor {
    private boolean myHasRaise = false;
    private boolean myHasReturn = false;
    @Nullable private PyExpression myRaiseTarget = null;

    @Override
    public void visitPyRaiseStatement(@NotNull PyRaiseStatement node) {
      myHasRaise = true;
      final PyExpression[] expressions = node.getExpressions();
      if (expressions.length > 0) {
        myRaiseTarget = expressions[0];
      }
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      myHasReturn = true;
    }

    @NotNull
    public String getRaiseTargetText() {
      if (myRaiseTarget != null) {
        String raiseTarget = myRaiseTarget.getText();
        if (myRaiseTarget instanceof PyCallExpression) {
          final PyExpression callee = ((PyCallExpression)myRaiseTarget).getCallee();
          if (callee != null) {
            raiseTarget = callee.getText();
          }
        }
        return raiseTarget;
      }
      return "";
    }
  }
}

