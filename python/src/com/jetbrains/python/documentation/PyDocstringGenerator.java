/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.google.common.collect.Maps;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
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
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.debugger.PySignatureUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

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

  private final Map<String, Pair<Integer, Integer>> myParamTypesOffset = Maps.newHashMap();
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

  public void addFunctionArguments(@NotNull PyFunction function,
                                   @Nullable PySignature signature) {
    for (PyParameter functionParam : function.getParameterList().getParameters()) {
      String paramName = functionParam.getName();
      if (!functionParam.isSelf() && !StringUtil.isEmpty(paramName)) {
        assert paramName != null;

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

  public void withReturn() {
    myGenerateReturn = true;
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

    int offset = getStartOffset();
    if (offset > 0) {
      builder.replaceRange(TextRange.create(offset, getEndOffset()), getDefaultType());

      Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();

      final VirtualFile virtualFile = myFile.getVirtualFile();
      if (virtualFile == null) return;
      OpenFileDescriptor descriptor = new OpenFileDescriptor(
        myProject, virtualFile, myDocStringOwner.getTextOffset() + myDocStringOwner.getTextLength()
      );
      Editor targetEditor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
      if (targetEditor != null) {
        targetEditor.getCaretModel().moveToOffset(myDocStringExpression.getTextOffset());
        TemplateManager.getInstance(myProject).startTemplate(targetEditor, template);
      }
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
  public String addParamToDocstring() {
    String text = getDocstringText();

    StructuredDocString structuredDocString = DocStringUtil.parse(text);

    Collection<DocstringParam> paramsToAdd = getParamsToAdd(structuredDocString, myParams);

    String[] lines = LineTokenizer.tokenize(text, true);
    if (lines.length == 1) {
      return createSingleLineReplacement(paramsToAdd);
    }
    else {
      return createMultiLineReplacement(lines, paramsToAdd);
    }
  }

  public boolean haveParametersToAdd() {
    Collection<DocstringParam> paramsToAdd = collectParametersToAdd();

    return paramsToAdd.size() > 0;
  }

  private Collection<DocstringParam> collectParametersToAdd() {
    String text = getDocstringText();

    StructuredDocString structuredDocString = DocStringUtil.parse(text); //TODO: do we need to cache it?

    return getParamsToAdd(structuredDocString, myParams);
  }

  @NotNull
  private String getDocstringText() {
    final PyStringLiteralExpression docstring = myDocStringOwner.getDocStringExpression();

    return docstring != null ? docstring.getText() : "\"\"\"\"\"\"";
  }

  public static Collection<DocstringParam> getParamsToAdd(final StructuredDocString structuredDocString,
                                                          List<DocstringParam> params) {
    return Collections2.filter(params, new Predicate<DocstringParam>() {
      @Override
      public boolean apply(DocstringParam input) {
        Substring s = structuredDocString != null ? structuredDocString.getParamByNameAndKind(input.getName(), input.getKind()) : null;
        return s == null;
      }
    });
  }

  @NotNull
  private String createMultiLineReplacement(String[] lines, Collection<DocstringParam> paramsToAdd) {
    StringBuilder replacementText = new StringBuilder();
    int ind = lines.length - 1;
    for (int i = 0; i != lines.length - 1; ++i) {
      String line = lines[i];
      if (isPlaceToInsertParameter(line)) {
        ind = i;
        break;
      }
      replacementText.append(line);
    }
    if (replacementText.length() > 0)
      replacementText.deleteCharAt(replacementText.length()-1);
    addParams(replacementText, false, paramsToAdd);
    for (int i = ind; i != lines.length; ++i) {
      String line = lines[i];
      replacementText.append(line);
    }

    return replacementText.toString();
  }

  private boolean isPlaceToInsertParameter(String line) {
    return line.contains(getPrefix());    //TODO: use regexp here?
  }

  private int addParams(StringBuilder replacementText,
                        boolean addWS, Collection<DocstringParam> paramsToAdd) {

    final String ws = getWhitespace();
    // if creating a new docstring, leave blank line where text will be entered
    if (!StringUtil.containsAlphaCharacters(replacementText.toString())) {
      replacementText.append("\n");
    }
    replacementText.append(ws);

    final Module module = ModuleUtilCore.findModuleForPsiElement(myDocStringOwner);
    if (module != null) {
      PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
      if (documentationSettings.isPlain(getFile())) return replacementText.length()-1;
    }

    int i = 0;

    for (DocstringParam param : paramsToAdd) {
      replacementText.append(getPrefix());
      replacementText.append(param.getKind());
      replacementText.append(" ");
      replacementText.append(param.getName());
      replacementText.append(": ");
      int startOffset = replacementText.length();
      int endOffset = startOffset;
      if (param.getType() != null) {
        replacementText.append(param.getType());
        endOffset += param.getType().length();
      }
      myParamTypesOffset.put(param.getName(), Pair.create(startOffset, endOffset));
      i++;
      if (i < paramsToAdd.size()) {
        replacementText.append(ws);
      }
    }

    if (myGenerateReturn && myDocStringOwner instanceof PyFunction) {
      PyFunction function = (PyFunction)myDocStringOwner;
      String returnType = PythonDocumentationProvider.generateRaiseOrReturn(function, " ", getPrefix(), true);
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
      if (document != null && statementList != null && myFunction != null && statementList.getStatements().length != 0
          && document.getLineNumber(statementList.getTextOffset()) != document.getLineNumber(myFunction.getTextOffset())) {
        whitespace = PsiTreeUtil.getPrevSiblingOfType(statementList, PsiWhiteSpace.class);
      }
    }
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 0) {
        ws += spaces[spaces.length - 1];
      }
    }
    else {
      ws += StringUtil.repeat(" ", getIndentSize(myDocStringOwner));
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

  public int getStartOffset() {
    Pair<Integer, Integer> offsets = getOffsets();
    return offsets != null ? offsets.first : -1;
  }

  private Pair<Integer, Integer> getOffsets() {
    DocstringParam paramToEdit = getParamToEdit();
    String paramName = paramToEdit.getName();
    return myParamTypesOffset.get(paramName);
  }

  private DocstringParam getParamToEdit() {
    if (myParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myParams.get(0);
  }

  public int getEndOffset() {
    Pair<Integer, Integer> offsets = getOffsets();
    return offsets != null ? offsets.second : -1;
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

      if (document != null && list != null) {
        if (document.getLineNumber(list.getTextOffset()) == document.getLineNumber(myFunction.getTextOffset()) ||
            list.getStatements().length == 0) {
          PyFunction func = elementGenerator.createFromText(LanguageLevel.forElement(myFunction),
                                                            PyFunction.class,
                                                            "def " + myFunction.getName() + myFunction.getParameterList().getText()
                                                            + ":\n" + StringUtil.repeat(" ", getIndentSize(myFunction))
                                                            + replacement + "\n" +
                                                            StringUtil.repeat(" ", getIndentSize(myFunction)) + list.getText());

          myFunction = (PyFunction)myFunction.replace(func);
        }
        else {
          PyExpressionStatement str = elementGenerator.createDocstring(replacement);
          list.addBefore(str, list.getStatements()[0]);
        }
      }

      myFunction = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(myFunction);
      myDocStringExpression = myFunction.getDocStringExpression();
    }
  }

  private static int getIndentSize(PyDocStringOwner function) {
    CodeStyleSettings.IndentOptions indentOptions = CodeStyleSettingsManager.
      getInstance(function.getProject()).getCurrentSettings().getIndentOptions(PythonFileType.INSTANCE);

    PyStatementList statementList = PsiTreeUtil.getParentOfType(function, PyStatementList.class);
    int indent = 1;
    while (statementList != null) {
      statementList = PsiTreeUtil.getParentOfType(statementList, PyStatementList.class);
      indent += 1;
    }
    return indent * indentOptions.TAB_SIZE;
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

  public PyDocstringGenerator withSignatures() {
    if (myFunction != null) {
      PySignature signature = PySignatureCacheManager.getInstance(myProject).findSignature(myFunction);

      addFunctionArguments(myFunction, signature);
    }
    return this;
  }
}

