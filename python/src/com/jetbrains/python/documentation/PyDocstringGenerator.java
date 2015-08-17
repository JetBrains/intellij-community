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
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.debugger.PySignatureUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

  private List<DocstringParam> myParams = Lists.newArrayList();
  private final Project myProject;
  private PyStringLiteralExpression myDocStringExpression;

  private PsiFile myFile;
  private boolean myGenerateReturn;

  public PyDocstringGenerator(@NotNull PyDocStringOwner docStringOwner) {
    myDocStringOwner = docStringOwner;
    if (docStringOwner instanceof PyFunction) {
      myFunction = (PyFunction)docStringOwner;
    }
    myProject = myDocStringOwner.getProject();
    myFile = myDocStringOwner.getContainingFile();
  }

  public PyDocstringGenerator withParam(@NotNull String kind, @NotNull String name) {
    return withParamTypedByName(kind, name, null);
  }

  public PyDocstringGenerator withParamTypedByQualifiedName(String kind, String name, @Nullable String type, @NotNull PsiElement anchor) {
    String typeName = type != null ? PySignatureUtil.getShortestImportableName(anchor, type) : null;
    return withParamTypedByName(kind, name, typeName);
  }

  public PyDocstringGenerator withParamTypedByName(String kind, String name, String type) {
    myParams.add(new DocstringParam(kind, name, type));
    return this;
  }

  public PyDocstringGenerator withReturn() {
    myGenerateReturn = true;
    return this;
  }

  public PyDocstringGenerator withSignatures() {
    if (myFunction != null) {
      PySignature signature = PySignatureCacheManager.getInstance(myProject).findSignature(myFunction);
      addParametersFromSignature(myFunction, signature);
    }
    return this;
  }

  private void addParametersFromSignature(@NotNull PyFunction function,
                                          @Nullable PySignature signature) {
    for (PyParameter functionParam : function.getParameterList().getParameters()) {
      String paramName = functionParam.getName();
      if (!functionParam.isSelf() && !StringUtil.isEmpty(paramName)) {
        String type = signature != null ? signature.getArgTypeQualifiedName(paramName) : null;

        if (type != null) {
          withParamTypedByQualifiedName("type", paramName, type, function);
        }
        else {
          withParam("param", paramName);
        }
      }
    }
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
          String raiseTarget = visitor.myRaiseTarget.getText();
          if (visitor.myRaiseTarget instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)visitor.myRaiseTarget).getCallee();
            if (callee != null) {
              raiseTarget = callee.getText();
            }
          }
          builder.append(" ").append(raiseTarget);
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
    Substring substring = null;
    if (paramToEdit.getKind().equals("type")) {
      substring = parsed.getParamTypeSubstring(paramName);
    }
    else if (paramToEdit.getKind().equals("rtype")){
      substring = parsed.getReturnTypeSubstring();
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
  private String addParamToDocstring() {
    String text = getDocstringText();

    StructuredDocString structuredDocString = DocStringUtil.parse(text);

    Collection<DocstringParam> paramsToAdd = filterOutExistingParameters(structuredDocString, myParams);

    String[] lines = LineTokenizer.tokenize(text, true);
    if (lines.length == 1) {
      return createSingleLineReplacement(paramsToAdd);
    }
    else {
      return createMultiLineReplacement(lines, paramsToAdd);
    }
  }

  public boolean haveParametersToAdd() {
    return !collectParametersToAdd().isEmpty();
  }

  private Collection<DocstringParam> collectParametersToAdd() {
    String text = getDocstringText();

    StructuredDocString structuredDocString = DocStringUtil.parse(text); //TODO: do we need to cache it?

    return filterOutExistingParameters(structuredDocString, myParams);
  }

  @NotNull
  private static Collection<DocstringParam> filterOutExistingParameters(final @Nullable StructuredDocString structuredDocString,
                                                                        @NotNull List<DocstringParam> params) {
    return Collections2.filter(params, new Predicate<DocstringParam>() {
      @Override
      public boolean apply(DocstringParam input) {
        Substring s = structuredDocString != null ? structuredDocString.getParamByNameAndKind(input.getName(), input.getKind()) : null;
        return s == null;
      }
    });
  }

  @NotNull
  private String getDocstringText() {
    final PyStringLiteralExpression docstring = myDocStringOwner.getDocStringExpression();

    return docstring != null ? docstring.getText() : "\"\"\"\"\"\"";
  }

  @NotNull
  private String createMultiLineReplacement(String[] lines, Collection<DocstringParam> paramsToAdd) {
    StringBuilder replacementText = new StringBuilder();
    int ind = lines.length - 1;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (isPlaceToInsertParameter(line)) {
        ind = i;
        break;
      }
      replacementText.append(line);
    }
    if (replacementText.length() > 0) {
      replacementText.deleteCharAt(replacementText.length() - 1);
    }
    addParams(replacementText, false, paramsToAdd);
    for (int i = ind; i < lines.length; i++) {
      String line = lines[i];
      replacementText.append(line);
    }

    return replacementText.toString();
  }

  private boolean isPlaceToInsertParameter(String line) {
    return line.contains(getPrefix());    //TODO: use regexp here?
  }

  private int addParams(@NotNull StringBuilder replacementText, boolean addWS, @NotNull Collection<DocstringParam> paramsToAdd) {

    final String ws = getWhitespace();
    // if creating a new docstring, leave blank line where text will be entered
    if (!StringUtil.containsAlphaCharacters(replacementText.toString())) {
      replacementText.append("\n");
    }
    replacementText.append(ws);

    final Module module = ModuleUtilCore.findModuleForPsiElement(myDocStringOwner);
    if (module != null) {
      PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
      if (documentationSettings.isPlain(getFile())) return replacementText.length() - 1;
    }

    final List<String> unindentedLines = new ArrayList<String>();
    for (DocstringParam param : paramsToAdd) {
      final StringBuilder lineBuilder = new StringBuilder();
      lineBuilder.append(getPrefix());
      lineBuilder.append(param.getKind());
      lineBuilder.append(" ");
      lineBuilder.append(param.getName());
      lineBuilder.append(": ");
      if (param.getType() != null) {
        lineBuilder.append(param.getType());
      }
      unindentedLines.add(lineBuilder.toString());
    }
    StringUtil.join(unindentedLines, ws, replacementText);

    if (myGenerateReturn && myDocStringOwner instanceof PyFunction) {
      PyFunction function = (PyFunction)myDocStringOwner;
      String returnType = generateRaiseOrReturn(function, " ", getPrefix(), true);
      if (!returnType.isEmpty()) {
        replacementText.append(ws).append(returnType);
      }
    }

    int offset = replacementText.length();
    if (addWS) {
      replacementText.append(ws);
    }
    else {
      replacementText.append("\n");
    }
    return offset;
  }

  private String getWhitespace() {
    PsiWhiteSpace whitespace = null;
    if (myDocStringOwner instanceof PyFunction) {
      final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(getFile());
      if (document != null && myFunction != null && !PyUtil.onSameLine(statementList, myFunction) && statementList.getStatements().length != 0) {
        whitespace = PsiTreeUtil.getPrevSiblingOfType(statementList, PsiWhiteSpace.class);
      }
    }
    String ws = "\n";
    if (whitespace != null) {
      final String whitespaceText = whitespace.getText();
      final int index = whitespaceText.lastIndexOf('\n');
      if (index >= 0) {
        ws += whitespaceText.substring(index + 1);
      }
    }
    else {
      ws += StringUtil.repeat(" ", calcExpectedIndentSize(myDocStringOwner));
    }
    return ws;
  }

  @NotNull
  private String createSingleLineReplacement(Collection<DocstringParam> paramsToAdd) {
    String text = getDocstringText();

    StringBuilder replacementText = new StringBuilder();
    String closingQuotes;
    if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
      replacementText.append(text.substring(0, text.length() - 3));
      closingQuotes = text.substring(text.length() - 3);
    }
    else {
      replacementText.append(text.substring(0, text.length()));
      closingQuotes = text.substring(text.length() - 1);
    }
    addParams(replacementText, true, paramsToAdd);
    replacementText.append(closingQuotes);
    return replacementText.toString();
  }

  public String docStringAsText() {
    return addParamToDocstring();
  }

  private DocstringParam getParamToEdit() {
    if (myParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myParams.get(0);
  }

  public void build() {
    myDocStringExpression = myDocStringOwner.getDocStringExpression();
    final String replacement = addParamToDocstring();

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(myProject);
    if (myDocStringExpression != null) {
      PyExpression str = elementGenerator.createDocstring(replacement).getExpression();
      myDocStringExpression.replace(str);
      if (myFunction != null) {
        myFunction = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myFunction);
      }
      PyDocStringOwner owner = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myDocStringOwner);
      if (owner != null) {
        myDocStringOwner = owner;
      }

      myDocStringExpression = myDocStringOwner.getDocStringExpression();
    }
    else {
      if (myFunction == null) {
        throw new IllegalStateException("Should be a function");
      }
      final PyStatementList list = myFunction.getStatementList();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(getFile());

      if (document != null) {
        if (PyUtil.onSameLine(list, myFunction) || list.getStatements().length == 0) {
          PyFunction func = elementGenerator.createFromText(LanguageLevel.forElement(myFunction),
                                                            PyFunction.class,
                                                            "def " + myFunction.getName() + myFunction.getParameterList().getText()
                                                            + ":\n" + StringUtil.repeat(" ", calcExpectedIndentSize(myFunction))
                                                            + replacement + "\n" +
                                                            StringUtil.repeat(" ", calcExpectedIndentSize(myFunction)) + list.getText());

          myFunction = (PyFunction)myFunction.replace(func);
        }
        else {
          PyExpressionStatement str = elementGenerator.createDocstring(replacement);
          list.addBefore(str, list.getStatements()[0]);
        }
      }

      CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myFunction);
      myDocStringExpression = myFunction.getDocStringExpression();
    }
  }

  private int calcExpectedIndentSize(@NotNull PyDocStringOwner function) {
    PyStatementList statementList = PsiTreeUtil.getParentOfType(function, PyStatementList.class);
    int indent = 1;
    while (statementList != null) {
      statementList = PsiTreeUtil.getParentOfType(statementList, PyStatementList.class);
      indent += 1;
    }
    return indent * getIndentSizeFromSettings();
  }

  private int getIndentSizeFromSettings() {
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();
    final CodeStyleSettings.IndentOptions indentOptions = codeStyleSettings.getIndentOptions(PythonFileType.INSTANCE);
    return indentOptions.INDENT_SIZE;
  }

  private String getPrefix() {
    String prefix = ":";
    final Module module = ModuleUtilCore.findModuleForPsiElement(myDocStringOwner);
    if (module == null) return prefix;

    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    if (documentationSettings.isEpydocFormat(getFile())) {
      prefix = "@";
    }
    return prefix;
  }

  public static class DocstringParam {
    private String myKind;
    private String myName;
    private String myType;

    private DocstringParam(@NotNull String kind, @NotNull String name, @Nullable String type) {
      myKind = kind;
      myName = name;
      myType = type;
    }

    public String getKind() {
      return myKind;
    }

    public String getName() {
      return myName;
    }

    public String getType() {
      return myType;
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
      if (expressions.length > 0) myRaiseTarget = expressions[0];
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      myHasReturn = true;
    }
  }
}

