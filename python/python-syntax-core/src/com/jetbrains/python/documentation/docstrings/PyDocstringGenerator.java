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
import com.google.common.collect.Maps;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.*;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.psi.PyAstElementGenerator;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.StructuredDocString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class PyDocstringGenerator {
  public static final String TRIPLE_DOUBLE_QUOTES = "\"\"\"";
  public static final String TRIPLE_SINGLE_QUOTES = "'''";

  private final List<DocstringParam> myAddedParams = new ArrayList<>();
  private final List<DocstringParam> myRemovedParams = new ArrayList<>();
  private final String myDocStringText;
  // Updated after buildAndInsert()
  private final @Nullable PyAstDocStringOwner myDocStringOwner;
  private final String myDocStringIndent;
  private final DocStringFormat myDocStringFormat;
  private final PsiElement mySettingsAnchor;

  private boolean myUseTypesFromDebuggerSignature = true;
  private boolean myNewMode; // true - generate new string, false - update existing
  private boolean myAddFirstEmptyLine = false;
  private boolean myParametersPrepared = false;
  private String myQuotes = TRIPLE_DOUBLE_QUOTES;

  private PyDocstringGenerator(@Nullable PyAstDocStringOwner docStringOwner,
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

  public static @NotNull PyDocstringGenerator forDocStringOwner(@NotNull PyAstDocStringOwner owner) {
    String indentation = "";
    if (owner instanceof PyAstStatementListContainer) {
      indentation = PyIndentUtil.getElementIndent(((PyAstStatementListContainer)owner).getStatementList());
    }
    final String docStringText = owner.getDocStringExpression() == null ? null : owner.getDocStringExpression().getText();
    return new PyDocstringGenerator(owner, docStringText, DocStringParser.getConfiguredDocStringFormatOrPlain(owner), indentation, owner);
  }

  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
   *                       It's needed to detect configured docstring format and Python indentation size and, as result,
   *                       generate properly formatted docstring.
   */
  public static @NotNull PyDocstringGenerator create(@NotNull DocStringFormat format,
                                            @NotNull String indentation,
                                            @NotNull PsiElement settingsAnchor) {
    return new PyDocstringGenerator(null, null, format, indentation, settingsAnchor);
  }

  public static @NotNull PyDocstringGenerator update(@NotNull PyAstStringLiteralExpression docString) {
    return new PyDocstringGenerator(PsiTreeUtil.getParentOfType(docString, PyAstDocStringOwner.class),
                                    docString.getText(),
                                    DocStringParser.getConfiguredDocStringFormatOrPlain(docString),
                                    PyIndentUtil.getElementIndent(docString),
                                    docString);
  }

  /**
   * @param settingsAnchor any PSI element, presumably in the same file/module where generated function is going to be inserted.
   *                       It's needed to detect configured docstring format and Python indentation size and, as result,
   *                       generate properly formatted docstring.
   */
  public static @NotNull PyDocstringGenerator update(@NotNull DocStringFormat format,
                                            @NotNull String indentation,
                                            @NotNull String text, PsiElement settingsAnchor) {
    return new PyDocstringGenerator(null, text, format, indentation, settingsAnchor);
  }

  public @NotNull PyDocstringGenerator withParam(@NotNull String name) {
    return withParamTypedByName(name, null);
  }

  public @NotNull PyDocstringGenerator withParam(@NotNull PyAstNamedParameter param) {
    return withParam(getPreferredParameterName(param));
  }

  public @NotNull PyDocstringGenerator withParamTypedByName(@NotNull String name, @Nullable String type) {
    myAddedParams.add(new DocstringParam(name, type, false));
    return this;
  }

  public @NotNull PyDocstringGenerator withParamTypedByName(@NotNull PyAstNamedParameter name, @Nullable String type) {
    return withParamTypedByName(getPreferredParameterName(name), type);
  }

  public @NotNull PyDocstringGenerator withReturnValue(@Nullable String type) {
    myAddedParams.add(new DocstringParam("", type, true));
    return this;
  }

  public @NotNull PyDocstringGenerator withoutParam(@NotNull String name) {
    myRemovedParams.add(new DocstringParam(name, null, false));
    return this;
  }

  public @NotNull PyDocstringGenerator withQuotes(@NotNull String quotes) {
    myQuotes = quotes;
    return this;
  }

  public @NotNull PyDocstringGenerator useTypesFromDebuggerSignature(boolean use) {
    myUseTypesFromDebuggerSignature = use;
    return this;
  }

  public @NotNull PyDocstringGenerator addFirstEmptyLine() {
    myAddFirstEmptyLine = true;
    return this;
  }


  public @NotNull PyDocstringGenerator forceNewMode() {
    myNewMode = true;
    return this;
  }

  /**
   * @param addReturn by default return declaration is added only if function body contains return statement. Sometimes it's not
   *                  possible, e.g. in  {@link com.jetbrains.python.editor.PythonEnterHandler} where unclosed docstring literal
   *                  "captures" whole function body including return statements. Keep in mind that declaration for the return value
   *                  won't be added if containing function is <tt>__init__</tt> or <tt>__new__</tt> method.
   */
  public @NotNull PyDocstringGenerator withInferredParameters(boolean addReturn) {
    if (myDocStringOwner instanceof PyAstFunction) {
      for (PyAstParameter param : ((PyAstFunction)myDocStringOwner).getParameterList().getParameters()) {
        if (param.getAsNamed() == null) {
          continue;
        }
        final String paramName = param.getName();
        final StructuredDocString docString = getStructuredDocString();
        if (StringUtil.isEmpty(paramName) || param.isSelf() || docString != null && docString.getParameters().contains(paramName)) {
          continue;
        }
        withParam((PyAstNamedParameter)param);
      }
      final RaiseVisitor visitor = new RaiseVisitor();
      final PyAstStatementList statementList = ((PyAstFunction)myDocStringOwner).getStatementList();
      statementList.accept(visitor);
      if (!PyUtilCore.isConstructorLikeMethod(myDocStringOwner) && (visitor.myHasReturn || addReturn)) {
        // will add :return: placeholder in Sphinx/Epydoc docstrings
        withReturnValue(null);
      }
    }
    return this;
  }

  public @NotNull String getDocStringIndent() {
    return myDocStringIndent;
  }

  public @NotNull DocStringFormat getDocStringFormat() {
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
    final Set<Pair<String, Boolean>> withoutType = new HashSet<>();
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
    if (myDocStringOwner instanceof PyAstFunction && myUseTypesFromDebuggerSignature) {
      signature = PySignatureCacheManager.getInstance(myDocStringOwner.getProject()).findSignature((PyAstFunction)myDocStringOwner);
    }
    final DocStringFormat format = myDocStringFormat;
    final ArrayList<DocstringParam> filtered = new ArrayList<>();
    final Set<Pair<String, Boolean>> processed = new HashSet<>();
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
          if (format != DocStringFormat.GOOGLE && format != DocStringFormat.NUMPY) {
            // In reST and Epydoc for each parameter add two tags, e.g. in reST (Sphinx)
            // :param foo:
            // :type foo:
            filtered.add(param);
          }
          filtered.add(new DocstringParam(param.getName(), type, param.isReturnValue()));
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

  public @Nullable PyAstStringLiteralExpression getDocStringExpression() {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");
    return myDocStringOwner.getDocStringExpression();
  }

  private @Nullable StructuredDocString getStructuredDocString() {
    return myDocStringText == null ? null : DocStringParser.parseDocString(myDocStringFormat, myDocStringText);
  }

  public @NotNull String getPreferredParameterName(@NotNull PyAstNamedParameter parameter) {
    if (getDocStringFormat() == DocStringFormat.GOOGLE) {
      return parameter.getAsNamed().getRepr(false);
    }
    return StringUtil.notNullize(parameter.getName());
  }


  public static @NotNull String getDefaultType(@NotNull DocstringParam param) {
    if (StringUtil.isEmpty(param.getType())) {
      return PyNames.OBJECT;
    }
    else {
      return param.getType();
    }
  }

  public @NotNull String buildDocString() {
    prepareParameters();
    if (myNewMode) {
      return createDocString();
    }
    else {
      return updateDocString();
    }
  }

  private @NotNull String createDocString() {
    DocStringBuilder builder = null;
    if (myDocStringFormat == DocStringFormat.REST) {
      builder = new TagBasedDocStringBuilder(SphinxDocString.TAG_PREFIX);
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
      builder = myDocStringFormat == DocStringFormat.GOOGLE
                ? GoogleCodeStyleDocStringBuilder.forSettings(mySettingsAnchor.getContainingFile())
                : new NumpyDocStringBuilder();
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

  private @NotNull String updateDocString() {
    DocStringUpdater updater = null;
    if (myDocStringFormat == DocStringFormat.REST) {
      // noinspection ConstantConditions
      updater = new TagBasedDocStringUpdater((TagBasedDocString)getStructuredDocString(), SphinxDocString.TAG_PREFIX, myDocStringIndent);
    }
    else if (myDocStringFormat == DocStringFormat.GOOGLE) {
      //noinspection ConstantConditions
      updater = GoogleCodeStyleDocStringUpdater.forSettings((GoogleCodeStyleDocString)getStructuredDocString(),
                                                            myDocStringIndent,
                                                            mySettingsAnchor.getContainingFile());
    }
    else if (myDocStringFormat == DocStringFormat.NUMPY) {
      //noinspection ConstantConditions
      updater = new NumpyDocStringUpdater((SectionBasedDocString)getStructuredDocString(), myDocStringIndent);
    }
    // plain docstring - do nothing
    else if (myDocStringText != null) {
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

  private @NotNull String createEmptyFallbackDocString() {
    return myQuotes + '\n' + myDocStringIndent + myQuotes;
  }

  public DocstringParam getParamToEdit() {
    if (myAddedParams.isEmpty()) {
      throw new IllegalStateException("We should have at least one param to edit");
    }
    return myAddedParams.get(0);
  }

  public @NotNull PyAstDocStringOwner buildAndInsert(@NotNull String replacementText) {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");

    final Project project = myDocStringOwner.getProject();
    PyAstElementGenerator elementGenerator = PyAstElementGenerator.getInstance(project);
    final PyAstExpressionStatement replacement = elementGenerator.createDocstring(replacementText);

    final PyAstStringLiteralExpression docStringExpression = getDocStringExpression();
    if (docStringExpression != null) {
      docStringExpression.replace(replacement.getExpression());
    }
    else {
      if (myDocStringOwner instanceof PyAstFile pyAstFile) {
        PsiElement lastCommentOrNull = null;
        for (PsiElement child : pyAstFile.getChildren()) {
          if (child instanceof PsiWhiteSpace) {
            continue;
          }
          if (child instanceof PsiComment) {
            lastCommentOrNull = child;
          }
          else {
            break;
          }
        }
        myDocStringOwner.addAfter(replacement, lastCommentOrNull);
        return myDocStringOwner;
      }
      PyAstStatementListContainer container = ObjectUtils.tryCast(myDocStringOwner, PyAstStatementListContainer.class);
      if (container == null) {
        throw new IllegalStateException("Should be a function or class");
      }
      final PyAstStatementList statements = container.getStatementList();
      final String indentation = PyIndentUtil.getElementIndent(statements);

      PyUtilCore.updateDocumentUnblockedAndCommitted(myDocStringOwner, document -> {
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

  public @NotNull PyAstDocStringOwner buildAndInsert() {
    Preconditions.checkNotNull(myDocStringOwner, "For this action docstring owner must be supplied");
    return buildAndInsert(buildDocString());
  }

  public static final class DocstringParam {

    private final String myName;
    private final String myType;
    private final boolean myReturnValue;

    private DocstringParam(@NotNull String name, @Nullable String type, boolean isReturn) {
      myName = name;
      myType = type;
      myReturnValue = isReturn;
    }

    public @NotNull String getName() {
      return myName;
    }

    public @Nullable String getType() {
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
      return Objects.equals(myType, param.myType);
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

  private static class RaiseVisitor extends PyAstRecursiveElementVisitor {

    private boolean myHasRaise = false;
    private boolean myHasReturn = false;
    private @Nullable PyAstExpression myRaiseTarget = null;

    @Override
    public void visitPyRaiseStatement(@NotNull PyAstRaiseStatement node) {
      myHasRaise = true;
      final PyAstExpression[] expressions = node.getExpressions();
      if (expressions.length > 0) {
        myRaiseTarget = expressions[0];
      }
    }

    @Override
    public void visitPyReturnStatement(@NotNull PyAstReturnStatement node) {
      myHasReturn = true;
    }

    public @NotNull String getRaiseTargetText() {
      if (myRaiseTarget != null) {
        String raiseTarget = myRaiseTarget.getText();
        if (myRaiseTarget instanceof PyAstCallExpression) {
          final PyAstExpression callee = ((PyAstCallExpression)myRaiseTarget).getCallee();
          if (callee != null) {
            raiseTarget = callee.getText();
          }
        }
        return raiseTarget;
      }
      return "";
    }
  }

  public @Nullable PyAstDocStringOwner getDocStringOwner() {
    return myDocStringOwner;
  }

  public List<DocstringParam> getAddedParams() {
    return myAddedParams;
  }
}

