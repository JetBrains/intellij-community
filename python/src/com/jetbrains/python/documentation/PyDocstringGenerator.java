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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.documentation.docstrings.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author traff
 */
public class PyDocstringGenerator {

  private final List<DocstringParam> myParams = Lists.newArrayList();
  // Updated after buildAndInsert
  @NotNull
  private PyDocStringOwner myDocStringOwner;

  private boolean myUseTypesFromDebuggerSignature = true;
  private boolean myNewMode = false; // true - generate new string, false - update existing
  private boolean myAddFirstEmptyLine = false;
  private boolean myParametersPrepared = false;
  private boolean myAlwaysGenerateReturn;
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

  /**
   * By default return declaration is added only if function body contains return statement. Sometimes it's not possible, e.g.
   * in  {@link com.jetbrains.python.editor.PythonEnterHandler} where unclosed docstring literal "captures" whole function body
   * including return statements.
   */
  @NotNull
  public PyDocstringGenerator forceAddReturn() {
    myAlwaysGenerateReturn = true;
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
          if (StringUtil.isEmpty(paramName) || param.isSelf() || docString != null && docString.getParameters().contains(paramName)) {
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
        final RaiseVisitor visitor = new RaiseVisitor();
        final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
        statementList.accept(visitor);
        if (visitor.myHasReturn || myAlwaysGenerateReturn) {
          // will add :return: placeholder in Sphinx/Epydoc docstrings
          myParams.add(new DocstringParam("", null, true));
          if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
            withReturnValue("");
          }
        }
      }
    }
    final DocStringFormat format = getDocStringFormat();
    if (format == DocStringFormat.GOOGLE || format == DocStringFormat.NUMPY) {
      // Google and Numpy docstring formats combine type and description in single declaration, thus
      // if both declaration with type and without it are requested, we should filter out duplicates
      final ArrayList<DocstringParam> copy = new ArrayList<DocstringParam>(myParams);
      for (final DocstringParam param : copy) {
        if (param.getType() == null) {
          final DocstringParam sameParamWithType = ContainerUtil.find(myParams, new Condition<DocstringParam>() {
            @Override
            public boolean value(DocstringParam other) {
              return other.isReturnValue() == param.isReturnValue() && other.getName().equals(param.getName()) && other.getType() != null;
            }
          });
          if (sameParamWithType != null) {
            myParams.remove(param);
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
    return DocStringUtil.parseDocString(format, expression);
  }

  public void startTemplate() {
    final PyStringLiteralExpression docStringExpression = getDocStringExpression();
    assert docStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

    if (myParams.size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    final DocstringParam paramToEdit = getParamToEdit();
    final DocStringFormat format = getDocStringFormat();
    if (format == DocStringFormat.PLAIN) {
      return;
    }
    final StructuredDocString parsed = DocStringUtil.parseDocString(format, docStringExpression);
    final Substring substring;
    if (paramToEdit.isReturnValue()) {
      substring = parsed.getReturnTypeSubstring();
    }
    else {
      final String paramName = paramToEdit.getName();
      substring = parsed.getParamTypeSubstring(paramName);
    }
    if (substring == null) {
      return;
    }
    builder.replaceRange(substring.getTextRange(), getDefaultType(getParamToEdit()));
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
  private static String getDefaultType(@NotNull DocstringParam param) {
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
    if (myNewMode) {
      return createDocString();
    }
    else {
      return updateDocString();
    }
  }

  @NotNull
  private String createDocString() {
    final String indentation = getDocStringIndentation();
    final DocStringFormat format = getDocStringFormat();
    DocStringBuilder builder = null;
    if (format == DocStringFormat.EPYTEXT || format == DocStringFormat.REST) {
      builder = new TagBasedDocStringBuilder(format == DocStringFormat.EPYTEXT ? "@" : ":");
      TagBasedDocStringBuilder tagBuilder = (TagBasedDocStringBuilder)builder;
      if (myAddFirstEmptyLine) {
        tagBuilder.addEmptyLine();
      }
      for (DocstringParam param : myParams) {
        if (param.isReturnValue()) {
          if (param.getType() != null) {
            tagBuilder.addReturnValueType(param.getType());
          }
          else {
            tagBuilder.addReturnValueDescription("");
          }
        }
        else {
          if (param.getType() != null) {
            tagBuilder.addParameterType(param.getName(), param.getType());
          }
          else {
            tagBuilder.addParameterDescription(param.getName(), "");
          }
        }
      }
    }
    else if (format == DocStringFormat.GOOGLE || format == DocStringFormat.NUMPY) {
      builder = format == DocStringFormat.GOOGLE ? new GoogleCodeStyleDocStringBuilder() : new NumpyDocStringBuilder();
      final SectionBasedDocStringBuilder sectionBuilder = (SectionBasedDocStringBuilder)builder;
      if (myAddFirstEmptyLine) {
        sectionBuilder.addEmptyLine();
      }
      final List<DocstringParam> parameters = ContainerUtil.findAll(myParams, new Condition<DocstringParam>() {
        @Override
        public boolean value(DocstringParam param) {
          return !param.isReturnValue();
        }
      });
      if (!parameters.isEmpty()) {
        sectionBuilder.startParametersSection();
        for (DocstringParam param : parameters) {
          sectionBuilder.addParameter(param.getName(), param.getType(), "");
        }
      }

      final List<DocstringParam> returnValues = ContainerUtil.findAll(myParams, new Condition<DocstringParam>() {
        @Override
        public boolean value(DocstringParam param) {
          return param.isReturnValue();
        }
      });

      if (!returnValues.isEmpty()) {
        sectionBuilder.startReturnsSection();
        boolean hasTypedReturns = false;
        for (DocstringParam returnValue : returnValues) {
          if (returnValue.getType() != null) {
            sectionBuilder.addReturnValue(null, getDefaultType(returnValue), "");
            hasTypedReturns = true;
          }
        }
        if (!hasTypedReturns) {
          sectionBuilder.addEmptyLine();
        }
      }
    }
    if (builder != null && !builder.getLines().isEmpty()) {
      return myQuotes + '\n' + builder.buildContent(indentation, true) + '\n' + indentation + myQuotes;
    }
    return myQuotes + '\n' + indentation + myQuotes;
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
    DocStringUpdater updater = null;
    final String docStringIndent = getDocStringIndentation();
    if (format == DocStringFormat.EPYTEXT || format == DocStringFormat.REST) {
      final String prefix = format == DocStringFormat.EPYTEXT ? "@" : ":";
      //noinspection unchecked,ConstantConditions
      updater = new TagBasedDocStringUpdater((TagBasedDocString)getStructuredDocString(), prefix, docStringIndent);
    }
    else if (format == DocStringFormat.GOOGLE) {
      //noinspection ConstantConditions
      updater = new GoogleCodeStyleDocStringUpdater((SectionBasedDocString)getStructuredDocString(), docStringIndent);
    }
    else if (format == DocStringFormat.NUMPY) {
      //noinspection ConstantConditions
      updater = new NumpyDocStringUpdater((SectionBasedDocString)getStructuredDocString(), docStringIndent);
    }
    if (updater != null) {
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
        final PsiElement beforeStatements = statements.getPrevSibling();
        final boolean onSameLine = !(beforeStatements instanceof PsiWhiteSpace) || !beforeStatements.textContains('\n');
        if (onSameLine || statements.getStatements().length == 0) {
          String replacementWithLineBreaks = "\n" + indentation + replacementText;
          if (statements.getStatements().length > 0) {
            replacementWithLineBreaks += "\n" + indentation;
          }
          final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
          documentManager.doPostponedOperationsAndUnblockDocument(document);
          final TextRange range = beforeStatements.getTextRange();
          try {
            if (beforeStatements instanceof PsiWhiteSpace) {
              if (statements.getStatements().length > 0) {
                document.replaceString(range.getStartOffset(), range.getEndOffset(), replacementWithLineBreaks);
              }
              else {
                // preserve original spacing, since it probably separates function and other declarations
                document.insertString(range.getStartOffset(), replacementWithLineBreaks);
              }
            }
            else {
              document.insertString(range.getEndOffset(), replacementWithLineBreaks);
            }
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

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      DocstringParam param = (DocstringParam)o;

      if (myReturnValue != param.myReturnValue) return false;
      if (!myName.equals(param.myName)) return false;
      if (myType != null ? !myType.equals(param.myType) : param.myType != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = myName.hashCode();
      result = 31 * result + (myType != null ? myType.hashCode() : 0);
      result = 31 * result + (myReturnValue ? 1 : 0);
      return result;
    }

    @Override
    public String toString() {
      return "DocstringParam{" +
             "myName='" + myName + '\'' +
             ", myType='" + myType + '\'' +
             ", myReturnValue=" + myReturnValue +
             '}';
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

