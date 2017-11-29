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
package com.jetbrains.python.documentation.docstrings;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.template.*;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.toolbox.Substring;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author traff
 */
public class PyDocstringGenerator {
  public static final String TRIPLE_DOUBLE_QUOTES = "\"\"\"";
  public static final String TRIPLE_SINGLE_QUOTES = "'''";
  
  private final List<DocstringParam> myAddedParams = Lists.newArrayList();
  private final List<DocstringParam> myRemovedParams = Lists.newArrayList();
  private final String myDocStringText;
  // Updated after buildAndInsert()
  @Nullable private PyDocStringOwner myDocStringOwner;
  private final String myDocStringIndent;
  private final DocStringFormat myDocStringFormat;
  private final PsiElement mySettingsAnchor;

  private boolean myUseTypesFromDebuggerSignature = true;
  private boolean myNewMode = false; // true - generate new string, false - update existing
  private boolean myAddFirstEmptyLine = false;
  private boolean myParametersPrepared = false;
  private String myQuotes = TRIPLE_DOUBLE_QUOTES;

  private PyDocstringGenerator(@Nullable PyDocStringOwner docStringOwner,
                               @Nullable String docStringText,
                               @NotNull DocStringFormat format,
                               @NotNull String indentation,
                               @NotNull PsiElement settingsAnchor) {
    myDocStringOwner = docStringOwner;
    myDocStringIndent = indentation;
    myDocStringFormat = format;
    myDocStringText = docStringText;
    myNewMode = myDocStringText == null;
    mySettingsAnchor = settingsAnchor;
  }

  @NotNull
  public static PyDocstringGenerator forDocStringOwner(@NotNull PyDocStringOwner owner) {
    String indentation = "";
    if (owner instanceof PyStatementListContainer) {
      indentation = PyIndentUtil.getElementIndent(((PyStatementListContainer)owner).getStatementList());
    }
    final String docStringText = owner.getDocStringExpression() == null ? null : owner.getDocStringExpression().getText();
    return new PyDocstringGenerator(owner, docStringText, DocStringUtil.getConfiguredDocStringFormatOrPlain(owner), indentation, owner);
  }
  
  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
   *                       It's needed to detect configured docstring format and Python indentation size and, as result, 
   *                       generate properly formatted docstring. 
   */
  @NotNull
  public static PyDocstringGenerator create(@NotNull DocStringFormat format, @NotNull String indentation, @NotNull PsiElement settingsAnchor) {
    return new PyDocstringGenerator(null, null, format, indentation, settingsAnchor);
  }

  @NotNull
  public static PyDocstringGenerator update(@NotNull PyStringLiteralExpression docString) {
    return new PyDocstringGenerator(PsiTreeUtil.getParentOfType(docString, PyDocStringOwner.class),
                                    docString.getText(), 
                                    DocStringUtil.getConfiguredDocStringFormatOrPlain(docString),
                                    PyIndentUtil.getElementIndent(docString), 
                                    docString);
  }

  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
   *                       It's needed to detect configured docstring format and Python indentation size and, as result, 
   *                       generate properly formatted docstring. 
   */
  @NotNull
  public static PyDocstringGenerator update(@NotNull DocStringFormat format,
                                            @NotNull String indentation,
                                            @NotNull String text, PsiElement settingsAnchor) {
    return new PyDocstringGenerator(null, text, format, indentation, settingsAnchor);
  }

  @NotNull
  public PyDocstringGenerator withParam(@NotNull String name) {
    return withParamTypedByName(name, null);
  }

  @NotNull
  public PyDocstringGenerator withParam(@NotNull PyNamedParameter param) {
    return withParam(getPreferredParameterName(param));
  }

  @NotNull
  public PyDocstringGenerator withParamTypedByName(@NotNull String name, @Nullable String type) {
    myAddedParams.add(new DocstringParam(name, type, false));
    return this;
  }

  @NotNull
  public PyDocstringGenerator withParamTypedByName(@NotNull PyNamedParameter name, @Nullable String type) {
    return withParamTypedByName(getPreferredParameterName(name), type);
  }

  @NotNull
  public PyDocstringGenerator withReturnValue(@Nullable String type) {
    myAddedParams.add(new DocstringParam("", type, true));
    return this;
  }

  @NotNull
  public PyDocstringGenerator withoutParam(@NotNull String name) {
    myRemovedParams.add(new DocstringParam(name, null, false));
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
  public PyDocstringGenerator addFirstEmptyLine() {
    myAddFirstEmptyLine = true;
    return this;
  }


  @NotNull
  public PyDocstringGenerator forceNewMode() {
    myNewMode = true;
    return this;
  }

  /**
   * @param addReturn by default return declaration is added only if function body contains return statement. Sometimes it's not
   *                  possible, e.g. in  {@link com.jetbrains.python.editor.PythonEnterHandler} where unclosed docstring literal
   *                  "captures" whole function body including return statements. Keep in mind that declaration for the return value
   *                  won't be added if containing function is <tt>__init__</tt> or <tt>__new__</tt> method.
   */
  @NotNull
  public PyDocstringGenerator withInferredParameters(boolean addReturn) {
    if (myDocStringOwner instanceof PyFunction) {
      for (PyParameter param : ((PyFunction)myDocStringOwner).getParameterList().getParameters()) {
        if (param.getAsNamed() == null) {
          continue;
        }
        final String paramName = param.getName();
        final StructuredDocString docString = getStructuredDocString();
        if (StringUtil.isEmpty(paramName) || param.isSelf() || docString != null && docString.getParameters().contains(paramName)) {
          continue;
        }
        withParam((PyNamedParameter)param);
      }
      final RaiseVisitor visitor = new RaiseVisitor();
      final PyStatementList statementList = ((PyFunction)myDocStringOwner).getStatementList();
      statementList.accept(visitor);
      if (!isConstructor((PyFunction)myDocStringOwner) && (visitor.myHasReturn || addReturn)) {
        // will add :return: placeholder in Sphinx/Epydoc docstrings
        withReturnValue(null);
      }
    }
    return this;
  }

  private static boolean isConstructor(@NotNull PyFunction function) {
    final String funcName = function.getName();
    return PyNames.INIT.equals(funcName) && function.getContainingClass() != null;
  }

  @NotNull
  public String getDocStringIndent() {
    return myDocStringIndent;
  }

  @NotNull
  public DocStringFormat getDocStringFormat() {
    return myDocStringFormat;
  }

  public boolean isNewMode() {
    return myNewMode;
  }

  /**
   * Populate parameters for function if nothing was specified.
   * Order parameters, remove duplicates and merge parameters with and without type according to docstring format.
   */
  private void prepareParameters() {
    if (myParametersPrepared) {
      return;
    }
    final Set<Pair<String, Boolean>> withoutType = Sets.newHashSet();
    final Map<Pair<String, Boolean>, String> paramTypes = Maps.newHashMap();
    for (DocstringParam param : myAddedParams) {
      if (param.getType() == null) {
        withoutType.add(Pair.create(param.getName(), param.isReturnValue()));
      }
      else {
        // leave only the last type for parameter
        paramTypes.put(Pair.create(param.getName(), param.isReturnValue()), param.getType());
      }
    }

    // Sanitize parameters
    PySignature signature = null;
    if (myDocStringOwner instanceof PyFunction && myUseTypesFromDebuggerSignature) {
      signature = PySignatureCacheManager.getInstance(myDocStringOwner.getProject()).findSignature((PyFunction)myDocStringOwner);
    }
    final DocStringFormat format = myDocStringFormat;
    final ArrayList<DocstringParam> filtered = Lists.newArrayList();
    final Set<Pair<String, Boolean>> processed = Sets.newHashSet();
    for (DocstringParam param : myAddedParams) {
      final Pair<String, Boolean> paramCoordinates = Pair.create(param.getName(), param.isReturnValue());
      if (processed.contains(paramCoordinates)) {
        continue;
      }
      if (param.getType() == null) {
        String type = paramTypes.get(paramCoordinates);
        if (type == null && PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
          if (signature != null) {
            type = StringUtil.notNullize(param.isReturnValue() ? 
                                         signature.getReturnTypeQualifiedName() :
                                         signature.getArgTypeQualifiedName(param.getName()));
          }
          else {
            type = "";
          }
        }
        if (type != null) {
          // Google and Numpy docstring formats combine type and description in single declaration, thus
          // if both declaration with type and without it are requested, we should filter out duplicates
          if (format == DocStringFormat.GOOGLE || format == DocStringFormat.NUMPY) {
            filtered.add(new DocstringParam(param.getName(), type, param.isReturnValue()));
          }
          else {
            // In reST and Epydoc for each parameter add two tags, e.g. in reST (Sphinx)
            // :param foo:
            // :type foo:
            filtered.add(param);
            filtered.add(new DocstringParam(param.getName(), type, param.isReturnValue()));
          }
        }
        else {
          // no type was given and it's not required by settings
          filtered.add(param);
        }
      }
      else if (!withoutType.contains(paramCoordinates)) {
        filtered.add(param);
      }
      processed.add(paramCoordinates);
    }
    myAddedParams.clear();
    myAddedParams.addAll(filtered);
    myParametersPrepared = true;
  }

  public boolean hasParametersToAdd() {
    prepareParameters();
    return !myAddedParams.isEmpty();
  }

  @Nullable
  private PyStringLiteralExpression getDocStringExpression() {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");
    return myDocStringOwner.getDocStringExpression();
  }

  @Nullable
  private StructuredDocString getStructuredDocString() {
    return myDocStringText == null ? null : DocStringUtil.parseDocString(myDocStringFormat, myDocStringText);
  }

  public void startTemplate() {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");
    final PyStringLiteralExpression docStringExpression = getDocStringExpression();
    assert docStringExpression != null;

    final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(docStringExpression);

    if (myAddedParams.size() > 1) {
      throw new IllegalArgumentException("TemplateBuilder can be created only for one parameter");
    }

    final DocstringParam paramToEdit = getParamToEdit();
    final DocStringFormat format = myDocStringFormat;
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
    Template template = PyUtil.updateDocumentUnblockedAndCommitted(myDocStringOwner, document -> {
      return ((TemplateBuilderImpl)builder).buildInlineTemplate();
    });
    final VirtualFile virtualFile = myDocStringOwner.getContainingFile().getVirtualFile();
    if (virtualFile == null) return;
    final Project project = myDocStringOwner.getProject();
    final Editor targetEditor = PsiUtilBase.findEditor(myDocStringOwner);
    if (targetEditor != null && template != null) {
      targetEditor.getCaretModel().moveToOffset(docStringExpression.getTextOffset());
      TemplateManager.getInstance(project).startTemplate(targetEditor, template);
    }
  }

  @NotNull
  public String getPreferredParameterName(@NotNull PyNamedParameter parameter) {
    if (getDocStringFormat() == DocStringFormat.GOOGLE && parameter.getAsNamed() != null) {
      return parameter.getAsNamed().getRepr(false);
    }
    return StringUtil.notNullize(parameter.getName());
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
    DocStringBuilder builder = null;
    if (myDocStringFormat == DocStringFormat.EPYTEXT || myDocStringFormat == DocStringFormat.REST) {
      builder = new TagBasedDocStringBuilder(myDocStringFormat == DocStringFormat.EPYTEXT ? "@" : ":");
      TagBasedDocStringBuilder tagBuilder = (TagBasedDocStringBuilder)builder;
      if (myAddFirstEmptyLine) {
        tagBuilder.addEmptyLine();
      }
      for (DocstringParam param : myAddedParams) {
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
    else if (myDocStringFormat == DocStringFormat.GOOGLE || myDocStringFormat == DocStringFormat.NUMPY) {
      builder = myDocStringFormat == DocStringFormat.GOOGLE ? GoogleCodeStyleDocStringBuilder.forProject(mySettingsAnchor.getProject()) : new NumpyDocStringBuilder();
      final SectionBasedDocStringBuilder sectionBuilder = (SectionBasedDocStringBuilder)builder;
      if (myAddFirstEmptyLine) {
        sectionBuilder.addEmptyLine();
      }
      final List<DocstringParam> parameters = ContainerUtil.findAll(myAddedParams, param -> !param.isReturnValue());
      if (!parameters.isEmpty()) {
        sectionBuilder.startParametersSection();
        for (DocstringParam param : parameters) {
          sectionBuilder.addParameter(param.getName(), param.getType(), "");
        }
      }

      final List<DocstringParam> returnValues = ContainerUtil.findAll(myAddedParams, param -> param.isReturnValue());

      if (!returnValues.isEmpty()) {
        sectionBuilder.startReturnsSection();
        boolean hasTypedReturns = false;
        for (DocstringParam returnValue : returnValues) {
          if (StringUtil.isNotEmpty(returnValue.getType())) {
            sectionBuilder.addReturnValue(null, returnValue.getType(), "");
            hasTypedReturns = true;
          }
        }
        if (!hasTypedReturns) {
          sectionBuilder.addEmptyLine();
        }
      }
    }
    if (builder != null && !builder.getLines().isEmpty()) {
      return myQuotes + '\n' + builder.buildContent(myDocStringIndent, true) + '\n' + myDocStringIndent + myQuotes;
    }
    return createEmptyFallbackDocString();
  }

  @NotNull
  private String updateDocString() {
    DocStringUpdater updater = null;
    if (myDocStringFormat == DocStringFormat.EPYTEXT || myDocStringFormat == DocStringFormat.REST) {
      final String prefix = myDocStringFormat == DocStringFormat.EPYTEXT ? "@" : ":";
      //noinspection unchecked,ConstantConditions
      updater = new TagBasedDocStringUpdater((TagBasedDocString)getStructuredDocString(), prefix, myDocStringIndent);
    }
    else if (myDocStringFormat == DocStringFormat.GOOGLE) {
      //noinspection ConstantConditions
      updater = GoogleCodeStyleDocStringUpdater.forProject((GoogleCodeStyleDocString)getStructuredDocString(), 
                                                           myDocStringIndent, 
                                                           mySettingsAnchor.getProject());
    }
    else if (myDocStringFormat == DocStringFormat.NUMPY) {
      //noinspection ConstantConditions
      updater = new NumpyDocStringUpdater((SectionBasedDocString)getStructuredDocString(), myDocStringIndent);
    }
    // plain docstring - do nothing
    else if (myDocStringText != null){
      return myDocStringText;
    }
    if (updater != null) {
      for (DocstringParam param : myAddedParams) {
        if (param.isReturnValue()) {
          updater.addReturnValue(param.getType());
        }
        else {
          updater.addParameter(param.getName(), param.getType());
        }
      }
      for (DocstringParam param : myRemovedParams) {
        if (!param.isReturnValue()) {
          updater.removeParameter(param.getName());
        }
      }
      return updater.getDocStringText();
    }
    return createEmptyFallbackDocString();
  }

  @NotNull
  private String createEmptyFallbackDocString() {
    return myQuotes + '\n' + myDocStringIndent + myQuotes;
  }

  private DocstringParam getParamToEdit() {
    if (myAddedParams.size() == 0) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myAddedParams.get(0);
  }

  @NotNull
  public PyDocStringOwner buildAndInsert() {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");
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
      final String indentation = PyIndentUtil.getElementIndent(statements);

      PyUtil.updateDocumentUnblockedAndCommitted(myDocStringOwner, document -> {
        final PsiElement beforeStatements = statements.getPrevSibling();
        String replacementWithLineBreaks = "\n" + indentation + replacementText;
        if (statements.getStatements().length > 0) {
          replacementWithLineBreaks += "\n" + indentation;
        }
        final TextRange range = beforeStatements.getTextRange();
        if (!(beforeStatements instanceof PsiWhiteSpace)) {
          document.insertString(range.getEndOffset(), replacementWithLineBreaks);
        }
        else if (statements.getStatements().length == 0 && beforeStatements.textContains('\n')) {
          // preserve original spacing, since it probably separates function and other declarations
          document.insertString(range.getStartOffset(), replacementWithLineBreaks);
        }
        else {
          document.replaceString(range.getStartOffset(), range.getEndOffset(), replacementWithLineBreaks);
        }
      });
    }
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

