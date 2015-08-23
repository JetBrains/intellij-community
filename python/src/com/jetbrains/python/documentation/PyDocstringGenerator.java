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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.docstrings.TagBasedDocStringBuilder;
import com.jetbrains.python.documentation.docstrings.TagBasedDocStringUpdater;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author traff
 */
public class PyDocstringGenerator {

  private final List<DocstringParam> myParams = Lists.newArrayList();
  // Updated after buildAndInsert
  @NotNull
  private PyDocStringOwner myDocStringOwner;

  private boolean myUseTypesFromDebuggerSignature = false;
  private boolean myNewMode = false; // true - generate new string, false - update existing
  private boolean myAddFirstEmptyLine = false;
  private boolean myParametersPrepared = false;
  private boolean myGenerateReturn;
  private String myQuotes = "\"\"\"";

  public PyDocstringGenerator(@NotNull PyDocStringOwner docStringOwner) {
    myDocStringOwner = docStringOwner;
    final PyStringLiteralExpression docStringExpression = myDocStringOwner.getDocStringExpression();
    myNewMode = docStringExpression == null;
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
  public PyDocstringGenerator withQuotes(@NotNull String quotes) {
    myQuotes = quotes;
    return this;
  }

  @NotNull
  public PyDocstringGenerator useTypesFromDebuggerSignature(boolean use) {
    myUseTypesFromDebuggerSignature = use;
    return this;
  }

  @NotNull
  public PyDocstringGenerator addReturn() {
    myGenerateReturn = true;
    return this;
  }

  @NotNull
  public PyDocstringGenerator addFirstEmptyLine() {
    myAddFirstEmptyLine = true;
    return this;
  }

  @NotNull
  public PyDocstringGenerator forceNewMode() {
    myNewMode = true;
    return this;
  }

  private void prepareParameters() {
    // Populate parameter list, if no one was specified explicitly
    if (!myParametersPrepared && myParams.isEmpty()) {
      if (myDocStringOwner instanceof PyFunction) {
        PySignature signature = null;
        if (myUseTypesFromDebuggerSignature) {
          signature = PySignatureCacheManager.getInstance(myDocStringOwner.getProject()).findSignature((PyFunction)myDocStringOwner);
        }
        for (PyParameter param : ((PyFunction)myDocStringOwner).getParameterList().getParameters()) {
          final String paramName = param.getName();
          final StructuredDocString docString = getStructuredDocString();
          if (StringUtil.isEmpty(paramName) || param.isSelf() || docString != null && docString.getParamTypeSubstring(paramName) != null) {
            continue;
          }
          final String signatureType = signature != null ? signature.getArgTypeQualifiedName(paramName) : null;
          String type = null;
          if (signatureType != null) {
            type = signatureType;
          }
          else if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
            type = "";
          }
          withParam(paramName);
          if (type != null) {
            withParamTypedByName(paramName, type);
          }
        }
        if (myGenerateReturn) {
          final RaiseVisitor visitor = new RaiseVisitor();
          final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
          statementList.accept(visitor);
          if (visitor.myHasReturn) {
            // will add :return: placeholder in Sphinx/Epydoc docstrings
            myParams.add(new DocstringParam("", null, true));
            if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
              withReturnValue("");
            }
          }
        }
      }
    }
    myParametersPrepared = true;
  }

  public boolean hasParametersToAdd() {
    prepareParameters();
    return !myParams.isEmpty();
  }

  @Nullable
  private PyStringLiteralExpression getDocStringExpression() {
    return myDocStringOwner.getDocStringExpression();
  }

  @NotNull
  private DocStringFormat getDocStringFormat() {
    return DocStringUtil.getDocStringFormat(myDocStringOwner);
  }

  @Nullable
  private StructuredDocString getStructuredDocString() {
    final PyStringLiteralExpression expression = getDocStringExpression();
    final DocStringFormat format = getDocStringFormat();
    if (format == DocStringFormat.PLAIN || expression == null) {
      return null;
    }
    return format.getProvider().parseDocString(expression);
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

  public void startTemplate() {
    final PyStringLiteralExpression docStringExpression = getDocStringExpression();
    assert docStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

    if (myParams.size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    final DocstringParam paramToEdit = getParamToEdit();
    final String paramName = paramToEdit.getName();
    final String stringContent = docStringExpression.getStringValue();
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
    builder.replaceRange(substring.getTextRange().shiftRight(docStringExpression.getStringValueTextRange().getStartOffset()),
                         getDefaultType());
    Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();
    final VirtualFile virtualFile = myDocStringOwner.getContainingFile().getVirtualFile();
    if (virtualFile == null) return;
    final Project project = myDocStringOwner.getProject();
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, docStringExpression.getTextOffset());
    Editor targetEditor = FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    if (targetEditor != null) {
      TemplateManager.getInstance(project).startTemplate(targetEditor, template);
    }
  }

  @NotNull
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
  public String buildDocString() {
    prepareParameters();
    if (isNewMode()) {
      return createDocString();
    }
    else {
      return updateDocString();
    }
  }

  private boolean isNewMode() {
    return myNewMode;
  }

  @NotNull
  private String createDocString() {
    final String indentation = getDocStringIndentation();
    final DocStringFormat format = getDocStringFormat();
    if (format == DocStringFormat.EPYTEXT || format == DocStringFormat.REST) {
      final TagBasedDocStringBuilder builder = new TagBasedDocStringBuilder(format == DocStringFormat.EPYTEXT ? "@" : ":");
      if (myAddFirstEmptyLine) {
        builder.addEmptyLine();
      }
      for (DocstringParam param : myParams) {
        if (param.isReturnValue()) {
          if (param.getType() != null) {
            builder.addReturnValueType(param.getType());
          }
          else {
            builder.addReturnValueDescription("");
          }
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
      if (builder.getLines().size() > 1) {
        return myQuotes + '\n' + builder.buildContent(indentation, true) + '\n' + indentation + myQuotes;
      }
      else {
        return myQuotes + builder.buildContent(indentation, false) + myQuotes;
      }
    }
    else {
      return myQuotes + '\n' + indentation + myQuotes;
    }
  }

  @NotNull
  private String getDocStringIndentation() {
    String indentation = "";
    if (myDocStringOwner instanceof PyStatementListContainer) {
      indentation = PyIndentUtil.getElementIndent(((PyStatementListContainer)myDocStringOwner).getStatementList());
    }
    return indentation;
  }

  @NotNull
  private String updateDocString() {
    final DocStringFormat format = getDocStringFormat();
    if (format == DocStringFormat.EPYTEXT || format == DocStringFormat.REST) {
      final String prefix = format == DocStringFormat.EPYTEXT ? "@" : ":";
      //noinspection unchecked,ConstantConditions
      TagBasedDocStringUpdater updater = new TagBasedDocStringUpdater((TagBasedDocString)getStructuredDocString(),
                                                                      prefix, getDocStringIndentation());
      for (DocstringParam param : myParams) {
        if (param.isReturnValue()) {
          updater.addReturnValue(param.getType());
        }
        else {
          updater.addParameter(param.getName(), param.getType());
        }
      }
      return updater.getDocStringText();
    }
    return myQuotes + myQuotes;
  }

  private DocstringParam getParamToEdit() {
    if (myParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myParams.get(0);
  }

  @NotNull
  public PyDocStringOwner buildAndInsert() {
    final String replacementText = buildDocString();

    final Project project = myDocStringOwner.getProject();
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    final PyExpressionStatement replacement = elementGenerator.createDocstring(replacementText);

    final PyStringLiteralExpression docStringExpression = getDocStringExpression();
    if (docStringExpression != null) {
      docStringExpression.replace(replacement.getExpression());
    }
    else {
      PyStatementListContainer container = PyUtil.as(myDocStringOwner, PyStatementListContainer.class);
      if (container == null) {
        throw new IllegalStateException("Should be a function or class");
      }
      final PyStatementList statements = container.getStatementList();
      final String indentation = PyIndentUtil.getExpectedElementIndent(statements);
      final Document document = PsiDocumentManager.getInstance(project).getDocument(myDocStringOwner.getContainingFile());

      if (document != null) {
        if (PyUtil.onSameLine(statements, myDocStringOwner) || statements.getStatements().length == 0) {
          PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
          String replacementWithLineBreaks = "\n" + indentation + replacementText;
          if (statements.getStatements().length > 0) {
            replacementWithLineBreaks += "\n" + indentation;
          }
          documentManager.doPostponedOperationsAndUnblockDocument(document);
          try {
            document.insertString(statements.getTextOffset(), replacementWithLineBreaks);
          }
          finally {
            documentManager.commitDocument(document);
          }
        }
        else {
          statements.addBefore(replacement, statements.getStatements()[0]);
        }
      }
    }
    myDocStringOwner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myDocStringOwner);
    return myDocStringOwner;
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

