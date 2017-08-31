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
package com.jetbrains.python.documentation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.*;
import com.jetbrains.python.console.PyConsoleUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyStructuredDocstringFormatter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RootVisitor;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;

public class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private ChainIterable<String> myResult;
  private final ChainIterable<String> myProlog;      // sequence for reassignment info, etc
  private final ChainIterable<String> myBody;        // sequence for doc string
  private final ChainIterable<String> myEpilog;      // sequence for doc "copied from" notices and such

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");
  private final ChainIterable<String> myReassignmentChain;

  public PyDocumentationBuilder(PsiElement element, PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myResult = new ChainIterable<>();
    myProlog = new ChainIterable<>();
    myBody = new ChainIterable<>();
    myEpilog = new ChainIterable<>();

    myResult.add(myProlog).addWith(TagCode.withAttribute("class", "descclassname"), myBody).add(myEpilog); // pre-assemble; then add stuff to individual cats as needed
    myResult = wrapInTag("html", wrapInTag("body", myResult));
    myReassignmentChain = new ChainIterable<>();
  }

  @Nullable
  public String build() {
    final TypeEvalContext context = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
    final PsiElement outerElement = myOriginalElement != null ? myOriginalElement.getParent() : null;

    final PsiElement elementDefinition = resolveToDocStringOwner(context);
    final boolean isProperty = buildFromProperty(elementDefinition, outerElement, context);

    if (myProlog.isEmpty() && !isProperty && !isAttribute()) {
      myProlog.add(myReassignmentChain);
    }

    if (elementDefinition instanceof PyDocStringOwner) {
      buildFromDocstring(elementDefinition, isProperty);
    }
    else if (isAttribute()) {
      buildFromAttributeDoc();
    }
    else if (elementDefinition instanceof PyNamedParameter) {
      buildFromParameter(context, outerElement, elementDefinition);
    }
    else if (elementDefinition != null && outerElement instanceof PyReferenceExpression) {
      myBody.addItem(combUp("\nInferred type: "));
      PythonDocumentationProvider
        .describeTypeWithLinks(context.getType((PyReferenceExpression)outerElement), context, outerElement, myBody);
    }

    if (elementDefinition != null) {
      final ASTNode node = elementDefinition.getNode();
      if (node != null && PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(node.getElementType())) {
        String documentationName = elementDefinition.getText();
        if (node.getElementType() == PyTokenTypes.AS_KEYWORD || node.getElementType() == PyTokenTypes.ELSE_KEYWORD) {
          final PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(elementDefinition, PyTryExceptStatement.class);
          if (statement != null) documentationName = "try";
        }
        else if (node.getElementType() == PyTokenTypes.IN_KEYWORD) {
          final PyForStatement statement = PsiTreeUtil.getParentOfType(elementDefinition, PyForStatement.class);
          if (statement != null) documentationName = "for";
        }
        buildForKeyword(documentationName);
      }
    }
    final String url = PythonDocumentationProvider.getUrlFor(myElement, myOriginalElement, false);
    if (url != null) {
      myEpilog.addItem(BR);
      myEpilog.addWith(TagBold, $("External documentation:"));
      myEpilog.addItem(BR);
      myEpilog.addItem("<a href=\"").addItem(url).addItem("\">").addItem(url).addItem("</a>");
    }

    if (myBody.isEmpty() && myEpilog.isEmpty()) {
      return null; // got nothing substantial to say!
    }
    else {
      return myResult.toString();
    }
  }

  private void buildForKeyword(@NotNull final String name) {
    try {
      final FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/python_keywords/" + name));
      try {
        final String text = FileUtil.loadTextAndClose(reader);
        myEpilog.addItem(StringUtil.convertLineSeparators(text, "\n"));
      }
      catch (IOException ignored) {
      }
      finally {
        try {
          reader.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    catch (FileNotFoundException ignored) {
    }
  }

  private void buildFromParameter(@NotNull final TypeEvalContext context, @Nullable final PsiElement outerElement,
                                  @NotNull final PsiElement elementDefinition) {
    myBody.addItem(combUp("Parameter " + PyUtil.getReadableRepr(elementDefinition, false)));
    final boolean typeFromDocstringAdded = addTypeAndDescriptionFromDocstring((PyNamedParameter)elementDefinition, context);
    if (outerElement instanceof PyExpression) {
      final PyType type = context.getType((PyExpression)outerElement);
      if (type != null) {
        String typeString = null;
        if (type instanceof PyDynamicallyEvaluatedType) {
          if (!typeFromDocstringAdded) {
            typeString = "\nDynamically inferred type: ";
          }
        }
        else {
          if (outerElement.getReference() != null) {
            final PsiElement target = outerElement.getReference().resolve();

            if (target instanceof PyTargetExpression) {
              final String targetName = ((PyTargetExpression)target).getName();
              if (targetName != null && targetName.equals(((PyNamedParameter)elementDefinition).getName())) {
                typeString = "\nReassigned value has type: ";
              }
            }
          }
        }
        if (typeString == null && !typeFromDocstringAdded) {
          typeString = "\nInferred type: ";
        }
        if (typeString != null) {
          myBody.addItem(combUp(typeString));
          PythonDocumentationProvider.describeTypeWithLinks(type, context, elementDefinition, myBody);
        }
      }
    }
  }

  private boolean buildFromProperty(PsiElement elementDefinition, @Nullable final PsiElement outerElement,
                                    @NotNull final TypeEvalContext context) {
    if (myOriginalElement == null) {
      return false;
    }
    final String elementName = myOriginalElement.getText();
    if (!PyNames.isIdentifier(elementName)) {
      return false;
    }
    if (!(outerElement instanceof PyQualifiedExpression)) {
      return false;
    }
    final PyExpression qualifier = ((PyQualifiedExpression)outerElement).getQualifier();
    if (qualifier == null) {
      return false;
    }
    final PyType type = context.getType(qualifier);
    if (!(type instanceof PyClassType)) {
      return false;
    }
    final PyClass cls = ((PyClassType)type).getPyClass();
    final Property property = cls.findProperty(elementName, true, null);
    if (property == null) {
      return false;
    }

    final AccessDirection direction = AccessDirection.of((PyElement)outerElement);
    final Maybe<PyCallable> accessor = property.getByDirection(direction);
    myProlog.addItem("property ").addWith(TagBold, $().addWith(TagCode, $(elementName)))
      .addItem(" of ").add(PythonDocumentationProvider.describeClass(cls, TagCode, true, true));
    if (accessor.isDefined() && property.getDoc() != null) {
      myBody.addItem(": ").addItem(property.getDoc()).addItem(BR);
    }
    else {
      final PyCallable getter = property.getGetter().valueOrNull();
      if (getter != null && getter != myElement && getter instanceof PyFunction) {
        // not in getter, getter's doc comment may be useful
        final PyStringLiteralExpression docstring = getEffectiveDocStringExpression((PyFunction)getter);
        if (docstring != null) {
          myProlog
            .addItem(BR).addWith(TagItalic, $("Copied from getter:")).addItem(BR)
            .addItem(docstring.getStringValue())
          ;
        }
      }
      myBody.addItem(BR);
    }
    myBody.addItem(BR);
    if (accessor.isDefined() && accessor.value() == null) elementDefinition = null;
    final String accessorKind = getAccessorKind(direction);
    if (elementDefinition != null) {
      myEpilog.addWith(TagSmall, $(BR, BR, accessorKind, " of property")).addItem(BR);
    }

    if (!(elementDefinition instanceof PyDocStringOwner)) {
      myBody.addWith(TagItalic, elementDefinition != null ? $("Declaration: ") : $(accessorKind + " is not defined.")).addItem(BR);
      if (elementDefinition != null) {
        myBody.addItem(combUp(PyUtil.getReadableRepr(elementDefinition, false)));
      }
    }
    return true;
  }

  @NotNull
  private static String getAccessorKind(@NotNull final AccessDirection dir) {
    final String accessorKind;
    if (dir == AccessDirection.READ) {
      accessorKind = "Getter";
    }
    else if (dir == AccessDirection.WRITE) {
      accessorKind = "Setter";
    }
    else {
      accessorKind = "Deleter";
    }
    return accessorKind;
  }

  private void buildFromDocstring(@NotNull final PsiElement elementDefinition, boolean isProperty) {
    PyClass pyClass = null;
    final PyStringLiteralExpression docStringExpression = getEffectiveDocStringExpression((PyDocStringOwner)elementDefinition);

    if (elementDefinition instanceof PyClass) {
      pyClass = (PyClass)elementDefinition;
      myBody.add(PythonDocumentationProvider.describeDecorators(pyClass, TagItalic, BR, LCombUp));
      myBody.add(PythonDocumentationProvider.describeClass(pyClass, TagBold, true, false));
    }
    else if (elementDefinition instanceof PyFunction) {
      final PyFunction pyFunction = (PyFunction)elementDefinition;
      if (!isProperty) {
        pyClass = pyFunction.getContainingClass();
        if (pyClass != null) {
          myBody.addWith(TagSmall, PythonDocumentationProvider.describeClass(pyClass, TagCode, true, true)).addItem(BR).addItem(BR);
        }
      }
      myBody.add(PythonDocumentationProvider.describeDecorators(pyFunction, TagItalic, BR, LCombUp))
        .add(PythonDocumentationProvider.describeFunction(pyFunction, TagBold, LCombUp));
      if (docStringExpression == null) {
        addInheritedDocString(pyFunction, pyClass);
      }
    }
    else if (elementDefinition instanceof PyFile) {
      addModulePath((PyFile)elementDefinition);
    }
    if (docStringExpression != null) {
      myBody.addItem(BR);
      addFormattedDocString(myElement, docStringExpression.getStringValue(), myBody, myEpilog);
    }
  }

  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  @Nullable
  private PsiElement resolveToDocStringOwner(@NotNull TypeEvalContext context) {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression) {
      final String targetName = myElement.getText();
      myReassignmentChain.addWith(TagSmall, $(PyBundle.message("QDOC.assigned.to.$0", targetName)).addItem(BR));
      final PyExpression assignedValue = ((PyTargetExpression)myElement).findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression) {
        final PsiElement resolved = resolveWithoutImplicits((PyReferenceExpression)assignedValue, context);
        if (resolved != null) {
          return resolved;
        }
      }
      return assignedValue;
    }
    if (myElement instanceof PyReferenceExpression) {
      myReassignmentChain.addWith(TagSmall, $(PyBundle.message("QDOC.assigned.to.$0", myElement.getText())).addItem(BR));
      return resolveWithoutImplicits((PyReferenceExpression)myElement, context);
    }
    // it may be a call to a standard wrapper
    if (myElement instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)myElement;
      final Pair<String, PyFunction> wrapInfo = PyCallExpressionHelper.interpretAsModifierWrappingCall(call);
      if (wrapInfo != null) {
        final String wrapperName = wrapInfo.getFirst();
        final PyFunction wrappedFunction = wrapInfo.getSecond();
        myReassignmentChain.addWith(TagSmall, $(PyBundle.message("QDOC.wrapped.in.$0", wrapperName)).addItem(BR));
        return wrappedFunction;
      }
    }
    return myElement;
  }

  @Nullable
  private static PsiElement resolveWithoutImplicits(@NotNull PyReferenceExpression element, @NotNull TypeEvalContext context) {
    final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);
    final QualifiedResolveResult resolveResult = element.followAssignmentsChain(resolveContext);
    return resolveResult.isImplicit() ? null : resolveResult.getElement();
  }

  private void addInheritedDocString(@NotNull final PyFunction pyFunction, @Nullable final PyClass pyClass) {
    boolean notFound = true;
    final String methodName = pyFunction.getName();
    if (pyClass == null || methodName == null) {
      return;
    }
    final boolean isConstructor = PyNames.INIT.equals(methodName);
    Iterable<PyClass> classes = pyClass.getAncestorClasses(null);
    if (isConstructor) {
      // look at our own class again and maybe inherit class's doc
      classes = new ChainIterable<>(pyClass).add(classes);
    }
    for (PyClass ancestor : classes) {
      PyStringLiteralExpression docstringElement = null;
      PyFunction inherited = null;
      boolean isFromClass = false;
      if (isConstructor) docstringElement = getEffectiveDocStringExpression(pyClass);
      if (docstringElement != null) {
        isFromClass = true;
      }
      else {
        inherited = ancestor.findMethodByName(methodName, false, null);
      }
      if (inherited != null) {
        docstringElement = getEffectiveDocStringExpression(inherited);
      }
      if (docstringElement != null) {
        final String inheritedDoc = docstringElement.getStringValue();
        if (inheritedDoc.length() > 1) {
          myEpilog.addItem(BR).addItem(BR);
          final String ancestorName = ancestor.getName();
          final String marker =
            (pyClass == ancestor) ? PythonDocumentationProvider.LINK_TYPE_CLASS : PythonDocumentationProvider.LINK_TYPE_PARENT;
          final String ancestorLink =
            $().addWith(new LinkWrapper(marker + ancestorName), $(ancestorName)).toString();
          if (isFromClass) {
            myEpilog.addItem(PyBundle.message("QDOC.copied.from.class.$0", ancestorLink));
          }
          else {
            myEpilog.addItem(PyBundle.message("QDOC.copied.from.$0.$1", ancestorLink, methodName));
          }
          myEpilog.addItem(BR).addItem(BR);
          final ChainIterable<String> formatted = new ChainIterable<>();
          final ChainIterable<String> unformatted = new ChainIterable<>();
          addFormattedDocString(pyFunction, inheritedDoc, formatted, unformatted);
          myEpilog.addWith(TagCode, formatted).add(unformatted);
          notFound = false;
          break;
        }
      }
    }

    if (notFound) {
      // above could have not worked because inheritance is not searched down to 'object'.
      // for well-known methods, copy built-in doc string.
      // TODO: also handle predefined __xxx__ that are not part of 'object'.
      if (PyNames.UNDERSCORED_ATTRIBUTES.contains(methodName)) {
        addPredefinedMethodDoc(pyFunction, methodName);
      }
    }
  }

  private void addPredefinedMethodDoc(PyFunction fun, String methodName) {
    final PyClassType objectType = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objectType != null) {
      final PyClass objectClass = objectType.getPyClass();
      final PyFunction predefinedMethod = objectClass.findMethodByName(methodName, false, null);
      if (predefinedMethod != null) {
        final PyStringLiteralExpression predefinedDocstring = getEffectiveDocStringExpression(predefinedMethod);
        final String predefinedDoc = predefinedDocstring != null ? predefinedDocstring.getStringValue() : null;
        if (predefinedDoc != null && predefinedDoc.length() > 1) { // only a real-looking doc string counts
          addFormattedDocString(fun, predefinedDoc, myBody, myBody);
          myEpilog.addItem(BR).addItem(BR).addItem(PyBundle.message("QDOC.copied.from.builtin"));
        }
      }
    }
  }

  private static void addFormattedDocString(@NotNull PsiElement element,
                                            @NotNull String docstring,
                                            @NotNull ChainIterable<String> formattedOutput,
                                            @NotNull ChainIterable<String> unformattedOutput) {
    final Project project = element.getProject();

    final List<String> formatted = PyStructuredDocstringFormatter.formatDocstring(element, docstring);
    if (formatted != null) {
      unformattedOutput.add(formatted);
      return;
    }

    boolean isFirstLine;
    final List<String> result = new ArrayList<>();
    final String[] lines = removeCommonIndentation(docstring);

    // reconstruct back, dropping first empty fragment as needed
    isFirstLine = true;
    final int tabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(PythonFileType.INSTANCE);
    for (String line : lines) {
      if (isFirstLine && ourSpacesPattern.matcher(line).matches()) continue; // ignore all initial whitespace
      if (isFirstLine) {
        isFirstLine = false;
      }
      else {
        result.add(BR);
      }
      int leadingTabs = 0;
      while (leadingTabs < line.length() && line.charAt(leadingTabs) == '\t') {
        leadingTabs++;
      }
      if (leadingTabs > 0) {
        line = StringUtil.repeatSymbol(' ', tabSize * leadingTabs) + line.substring(leadingTabs);
      }
      result.add(combUp(line));
    }
    formattedOutput.add(result);
  }

  /**
   * Adds type and description representation from function docstring
   *
   * @param parameter parameter of a function
   * @param context   type evaluation context
   * @return true if type from docstring was added
   */
  private boolean addTypeAndDescriptionFromDocstring(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      final String docString = PyPsiUtils.strValue(getEffectiveDocStringExpression(function));
      final Pair<String, String> typeAndDescr = getTypeAndDescription(docString, parameter);

      final String type = typeAndDescr.first;
      final String description = typeAndDescr.second;

      if (type != null) {
        final PyType pyType = PyTypeParser.getTypeByName(parameter, type, context);
        if (pyType instanceof PyClassType) {
          myBody.addItem(": ").addWith(new LinkWrapper(PythonDocumentationProvider.LINK_TYPE_PARAM), $(pyType.getName()));
        }
        else {
          myBody.addItem(": ").addItem(type);
        }
      }

      if (description != null) {
        myEpilog.addItem(BR).addItem(description);
      }

      return type != null;
    }

    return false;
  }

  private static Pair<String, String> getTypeAndDescription(@Nullable final String docString, @NotNull final PyNamedParameter followed) {
    String type = null;
    String desc = null;
    if (docString != null) {
      final StructuredDocString structuredDocString = DocStringUtil.parse(docString);
      final String name = followed.getName();
      type = structuredDocString.getParamType(name);
      desc = structuredDocString.getParamDescription(name);
    }
    return Pair.create(type, desc);
  }

  private void buildFromAttributeDoc() {
    final PyClass cls = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
    assert cls != null;
    final String type = PyUtil.isInstanceAttribute((PyExpression)myElement) ? "Instance attribute " : "Class attribute ";
    myProlog
      .addItem(type).addWith(TagBold, $().addWith(TagCode, $(((PyTargetExpression)myElement).getName())))
      .addItem(" of class ").addWith(PythonDocumentationProvider.LinkMyClass, $().addWith(TagCode, $(cls.getName()))).addItem(BR);

    final String docString = PyPsiUtils.strValue(getEffectiveDocStringExpression((PyTargetExpression)myElement));
    if (docString != null) {
      addFormattedDocString(myElement, docString, myBody, myEpilog);
    }
  }

  public static String[] removeCommonIndentation(@NotNull final String docstring) {
    // detect common indentation
    final String[] lines = LineTokenizer.tokenize(docstring, false);
    boolean isFirst = true;
    int cutWidth = Integer.MAX_VALUE;
    int firstIndentedLine = 0;
    for (String frag : lines) {
      if (frag.length() == 0) continue;
      int padWidth = 0;
      final Matcher matcher = ourSpacesPattern.matcher(frag);
      if (matcher.find()) {
        padWidth = matcher.end();
      }
      if (isFirst) {
        isFirst = false;
        if (padWidth == 0) {    // first line may have zero padding
          firstIndentedLine = 1;
          continue;
        }
      }
      if (padWidth < cutWidth) cutWidth = padWidth;
    }
    // remove common indentation
    if (cutWidth > 0 && cutWidth < Integer.MAX_VALUE) {
      for (int i = firstIndentedLine; i < lines.length; i += 1) {
        if (lines[i].length() >= cutWidth) {
          lines[i] = lines[i].substring(cutWidth);
        }
      }
    }
    final List<String> result = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith(PyConsoleUtil.ORDINARY_PROMPT)) break;
      result.add(line);
    }
    return ArrayUtil.toStringArray(result);
  }

  private void addModulePath(@NotNull PyFile followed) {
    // what to prepend to a module description?
    final VirtualFile file = followed.getVirtualFile();
    if (file == null) {
      myProlog.addWith(TagSmall, $(PyBundle.message("QDOC.module.path.unknown")));
    }
    else {
      QualifiedName name = QualifiedNameFinder.findShortestImportableQName(followed);
      if (name != null) {
        myProlog.add($("Module "))
          .addWith(TagBold, $(ObjectUtils.chooseNotNull(QualifiedNameFinder.canonizeQualifiedName(name, null), name).toString()));
      }
      else {
        String path = file.getPath();
        myProlog.addWith(TagSpan.withAttribute("path", path), $("").addWith(TagSmall, $(path)));
      }
    }
  }

  @Nullable
  static PyStringLiteralExpression getEffectiveDocStringExpression(@NotNull PyDocStringOwner owner) {
    final PyStringLiteralExpression expression = owner.getDocStringExpression();
    if (expression != null && StringUtil.isNotEmpty(PyPsiUtils.strValue(expression))) {
      return expression;
    }
    final PsiElement original = PyiUtil.getOriginalElement(owner);
    final PyDocStringOwner originalOwner = PyUtil.as(original, PyDocStringOwner.class);
    return originalOwner != null ? originalOwner.getDocStringExpression() : null;
  }

  private static class RootFinder implements RootVisitor {
    private String myResult;
    private final String myPath;

    private RootFinder(String path) {
      myPath = path;
    }

    @Override
    public boolean visitRoot(@NotNull VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource) {
      final String vpath = VfsUtilCore.urlToPath(root.getUrl());
      if (myPath.startsWith(vpath)) {
        myResult = vpath;
        return false;
      }
      else {
        return true;
      }
    }

    String getResult() {
      return myResult;
    }
  }
}
