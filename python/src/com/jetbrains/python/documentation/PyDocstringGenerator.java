package com.jetbrains.python.documentation;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.debugger.PySignatureUtil;
import com.jetbrains.python.psi.*;
import com.sun.javaws.exceptions.InvalidArgumentException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.plugin.dom.exception.InvalidStateException;

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

  public PyDocstringGenerator(@NotNull PyDocStringOwner docStringOwner) {
    myDocStringOwner = docStringOwner;
    if (docStringOwner instanceof PyFunction) {
      myFunction = (PyFunction)docStringOwner;
    }
    myProject = myDocStringOwner.getProject();
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

  private PsiFile getFile() {
    return myDocStringOwner.getContainingFile();
  }

  public void startTemplate() {
    assert myDocStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(myDocStringExpression);

    if (myParams.size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    builder.replaceRange(TextRange.create(getStartOffset(), getEndOffset()), getDefaultType());

    Template template = ((TemplateBuilderImpl)builder).buildInlineTemplate();

    OpenFileDescriptor descriptor = new OpenFileDescriptor(
      myProject,
      myDocStringOwner.getContainingFile().getVirtualFile(),
      myDocStringOwner.getTextOffset() + myDocStringOwner.getTextLength()
    );
    Editor targetEditor = FileEditorManager.getInstance(myProject).openTextEditor(descriptor, true);
    if (targetEditor != null) {
      targetEditor.getCaretModel().moveToOffset(myDocStringExpression.getTextOffset());
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
  public Pair<String, Integer> addParamToDocstring() {
    String text = getDocstringText();

    StructuredDocString structuredDocString = StructuredDocString.parse(text);

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

    StructuredDocString structuredDocString = StructuredDocString.parse(text);

    return getParamsToAdd(structuredDocString, myParams);
  }

  @NotNull
  private String getDocstringText() {
    final PyStringLiteralExpression docstring = myDocStringOwner.getDocStringExpression();

    return docstring != null ? docstring.getText() : "\"\"\"\"\"\"";
  }

  public static Collection<DocstringParam> getParamsToAdd(StructuredDocString structuredDocString,
                                                          List<DocstringParam> params) {
    final List<String> existingParameters =
      structuredDocString != null ? structuredDocString.getParameters() : Lists.<String>newArrayList();
    return Collections2.filter(params, new Predicate<DocstringParam>() {
      @Override
      public boolean apply(DocstringParam input) {
        return !existingParameters.contains(input.getName());
      }
    });
  }

  @NotNull
  private Pair<String, Integer> createMultiLineReplacement(String[] lines, Collection<DocstringParam> paramsToAdd) {
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
    int offset = addParams(replacementText, false, paramsToAdd);
    for (int i = ind; i != lines.length; ++i) {
      String line = lines[i];
      replacementText.append(line);
    }

    return new Pair<String, Integer>(replacementText.toString(), offset);
  }

  private boolean isPlaceToInsertParameter(String line) {
    return line.contains(getPrefix());    //TODO: use regexp here?
  }

  private int addParams(StringBuilder replacementText,
                        boolean addWS, Collection<DocstringParam> paramsToAdd) {

    PsiWhiteSpace whitespace = null;
    if (myDocStringOwner instanceof PyFunction) {
      final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
      final Document document = PsiDocumentManager.getInstance(myProject).getDocument(getFile());
      if (document != null && statementList != null && statementList.getStatements().length != 0
          && document.getLineNumber(statementList.getTextOffset()) != document.getLineNumber(myFunction.getTextOffset())) {
        whitespace = PsiTreeUtil.getPrevSiblingOfType(statementList, PsiWhiteSpace.class);
      }
    }
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 1) {
        ws += whitespace.getText().split("\n")[1];
      }
    }
    else {
      ws += StringUtil.repeat(" ", getIndentSize(myDocStringOwner));
    }
    if (replacementText.length() > 0) {
      replacementText.deleteCharAt(replacementText.length() - 1);
    }
    replacementText.append(ws);

    int i = 0;

    if (paramsToAdd.size() == 0) {
      throw new IllegalArgumentException("At least one parameter should be added");
    }

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

    int offset = replacementText.length();
    if (addWS) {
      replacementText.append(ws);
    }
    else {
      replacementText.append("\n");
    }
    return offset;
  }

  @NotNull
  private Pair<String, Integer> createSingleLineReplacement(Collection<DocstringParam> paramsToAdd) {
    String text = getDocstringText();

    StringBuilder replacementText = new StringBuilder();
    String closingQuotes;
    if (text.endsWith("'''") || text.endsWith("\"\"\"")) {
      replacementText.append(text.substring(0, text.length() - 2));
      closingQuotes = text.substring(text.length() - 3);
    }
    else {
      replacementText.append(text.substring(0, text.length()));
      closingQuotes = text.substring(text.length() - 1);
    }
    final int offset = addParams(replacementText, true, paramsToAdd);
    replacementText.append(closingQuotes);
    return new Pair<String, Integer>(replacementText.toString(), offset);
  }

  public String docStringAsText() {
    Pair<String, Integer> replacementToOffset = addParamToDocstring();
    return replacementToOffset.first;
  }

  public int getStartOffset() {
    DocstringParam paramToEdit = getParamToEdit();
    String paramName = paramToEdit.getName();
    return myParamTypesOffset.get(paramName).first;
  }

  private DocstringParam getParamToEdit() {
    if (myParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myParams.get(0);
  }

  public int getEndOffset() {
    DocstringParam paramToEdit = getParamToEdit();
    String paramName = paramToEdit.getName();
    return myParamTypesOffset.get(paramName).second;
  }

  private static class DocstringParam {
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
    final Pair<String, Integer> replacementToOffset =
      addParamToDocstring();

    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(myProject);
    if (myDocStringExpression != null) {
      PyExpression str = elementGenerator.createDocstring(replacementToOffset.getFirst()).getExpression();
      myDocStringExpression.replace(str);
      if (myFunction != null) {
        myFunction = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myFunction);
      }
      myDocStringOwner = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myDocStringOwner);
      myDocStringExpression = myDocStringOwner.getDocStringExpression();
    }
    else {
      if (myFunction == null) {
        throw new InvalidStateException("Should be a function");
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
                                                            + replacementToOffset.getFirst() + "\n" +
                                                            StringUtil.repeat(" ", getIndentSize(myFunction)) + list.getText());

          myFunction = (PyFunction)myFunction.replace(func);
        }
        else {
          PyExpressionStatement str = elementGenerator.createDocstring(replacementToOffset.getFirst());
          list.addBefore(str, list.getStatements()[0]);
        }
      }

      myFunction = CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(myFunction);
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
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(myProject);
    String prefix = ":";
    if (documentationSettings.isEpydocFormat(getFile())) {
      prefix = "@";
    }
    return prefix;
  }
}

