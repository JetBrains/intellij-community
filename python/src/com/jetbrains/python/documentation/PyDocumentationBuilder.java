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
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
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
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.*;
import com.jetbrains.python.console.PyConsoleUtil;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.PyStructuredDocstringFormatter;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RootVisitor;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;

public class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private final ChainIterable<String> myProlog;      // sequence for reassignment info, etc
  private final ChainIterable<String> myBody;        // sequence for doc string
  private final ChainIterable<String> myContent;
  private final ChainIterable<String> mySections;
  private final ChainIterable<String> myEpilog;      // sequence for doc "copied from" notices and such

  private final Map<String, ChainIterable<String>> mySectionsMap = FactoryMap.createMap(item -> new ChainIterable<>(), LinkedHashMap::new);

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");

  public PyDocumentationBuilder(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myProlog = new ChainIterable<>();
    myBody = new ChainIterable<>();
    myContent = new ChainIterable<>();
    mySections = new ChainIterable<>();
    myEpilog = new ChainIterable<>();
  }

  @Nullable
  public String build() {
    final TypeEvalContext context = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
    final PsiElement outerElement = myOriginalElement != null ? myOriginalElement.getParent() : null;

    final PsiElement elementDefinition = resolveToDocStringOwner(context);
    final boolean isProperty = buildFromProperty(elementDefinition, outerElement, context);

    if (elementDefinition instanceof PyDocStringOwner) {
      buildFromDocstring(((PyDocStringOwner)elementDefinition), isProperty);
    }
    else if (elementDefinition instanceof PyNamedParameter) {
      buildFromParameter((PyNamedParameter)elementDefinition, context);
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

    if (!mySectionsMap.isEmpty()) {
      mySections.addItem(DocumentationMarkup.SECTIONS_START);
      // FactoryMap's entrySet() returns pairs without particular order even for LinkedHashMap
      for (String header : mySectionsMap.keySet()) {
        mySections.addItem(DocumentationMarkup.SECTION_HEADER_START);
        mySections.addItem(header);
        mySections.addItem(DocumentationMarkup.SECTION_SEPARATOR);
        mySections.add(mySectionsMap.get(header));
        mySections.addItem(DocumentationMarkup.SECTION_END);
      }
      mySections.addItem(DocumentationMarkup.SECTIONS_END);
    }

    final String url = PythonDocumentationProvider.getUrlFor(myElement, myOriginalElement, false);
    if (url != null) {
      myEpilog.addItem(BR);
      myEpilog.addWith(TagBold, $("External documentation:"));
      myEpilog.addItem(BR);
      myEpilog.addItem("<a href=\"").addItem(url).addItem("\">").addItem(url).addItem("</a>");
    }

    if (myBody.isEmpty() && myContent.isEmpty() && myEpilog.isEmpty()) {
      return null; // got nothing substantial to say!
    }
    else {
      final ChainIterable<String> result = new ChainIterable<>();
      if (!myProlog.isEmpty() || !myBody.isEmpty()) {
        result.addItem(DocumentationMarkup.DEFINITION_START)
              .add(myProlog);

        if (!myBody.isEmpty() && !myProlog.isEmpty()) {
          result.addItem(BR);
        }

        result.add(myBody)
              .addItem(DocumentationMarkup.DEFINITION_END);
      }
      if (!myContent.isEmpty()) {
        result.addItem(DocumentationMarkup.CONTENT_START)
              .add(myContent)
              .addItem(DocumentationMarkup.CONTENT_END);
      }
      result.add(mySections).add(myEpilog); // pre-assemble; then add stuff to individual cats as needed
      return wrapInTag("html", wrapInTag("body", result)).toString();
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

  private void buildFromParameter(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final PyFunction func = PsiTreeUtil.getParentOfType(parameter, PyFunction.class, true, PyLambdaExpression.class);
    final String funcName = func == null ? PyNames.UNNAMED_ELEMENT : func.getName();
    myProlog
      .addItem("Parameter ")
      .addWith(TagBold, $().addWith(TagCode, $(parameter.getName())))
      .addItem(" of ")
      // TODO links to functions
      .addWith(TagCode, $(funcName));

    if (func != null) {
      final PyStringLiteralExpression docString = getEffectiveDocStringExpression(func);
      if (docString != null) {
        final StructuredDocString structuredDocString = DocStringUtil.parse(docString.getStringValue());
        final String description = structuredDocString.getParamDescription(parameter.getName());
        if (StringUtil.isNotEmpty(description)) {
          myContent.add($(description));
        }
      }
    }
    myBody.add(PythonDocumentationProvider.describeParameter(parameter, context));
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
            .addItem(" of ")
            .add(PythonDocumentationProvider.describeClass(cls, Function.identity(), TO_ONE_LINE_AND_ESCAPE, true, true, context));
    if (accessor.isDefined() && property.getDoc() != null) {
      myContent.add(formatDocString(elementDefinition, property.getDoc()));
    }
    else {
      final PyCallable getter = property.getGetter().valueOrNull();
      if (getter != null && getter != myElement && getter instanceof PyFunction) {
        // not in getter, getter's doc comment may be useful
        final PyStringLiteralExpression docstring = getEffectiveDocStringExpression((PyFunction)getter);
        if (docstring != null) {
          mySectionsMap.get(PyBundle.message("QDOC.documentation.is.copied.from")).addItem("property getter");
          myContent.add(formatDocString(elementDefinition, docstring.getStringValue()));
        }
      }
    }
    if (accessor.isDefined() && accessor.value() == null) elementDefinition = null;
    final String accessorKind = getAccessorKind(direction);
    mySectionsMap.get(PyBundle.message("QDOC.accessor.kind"))
                 .addItem(accessorKind)
                 .addItem(elementDefinition == null ? " (not defined)" : "");

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

  private void buildFromDocstring(@NotNull final PyDocStringOwner elementDefinition, boolean isProperty) {
    PyClass pyClass = null;
    final PyFunction pyFunction;
    final PyStringLiteralExpression docStringExpression = getEffectiveDocStringExpression(elementDefinition);
    if (docStringExpression != null) {
      myContent.add(formatDocString(myElement, docStringExpression.getStringValue()));
    }
    final TypeEvalContext context = TypeEvalContext.userInitiated(elementDefinition.getProject(), elementDefinition.getContainingFile());

    if (elementDefinition instanceof PyClass) {
      pyClass = (PyClass)elementDefinition;
      myBody.add(PythonDocumentationProvider.describeDecorators(pyClass, WRAP_IN_ITALIC, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, BR, BR));
      myBody
        .add(PythonDocumentationProvider.describeClass(pyClass, WRAP_IN_BOLD, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, false, true, context));
    }
    else if (elementDefinition instanceof PyFunction) {
      pyFunction = (PyFunction)elementDefinition;
      if (!isProperty) {
        pyClass = pyFunction.getContainingClass();
        if (pyClass != null) {
          myBody
            .addWith(TagSmall,
                     PythonDocumentationProvider.describeClass(pyClass, Function.identity(), TO_ONE_LINE_AND_ESCAPE, true, true, context))
            .addItem(BR)
            .addItem(BR);
        }
      }
      myBody.add(PythonDocumentationProvider.describeDecorators(pyFunction, WRAP_IN_ITALIC, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, BR, BR));
      myBody.add(PythonDocumentationProvider.describeFunction(pyFunction, WRAP_IN_BOLD, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, context));
      if (docStringExpression == null) {
        addInheritedDocString(pyFunction, pyClass);
      }
    }
    else if (elementDefinition instanceof PyFile) {
      addModulePath((PyFile)elementDefinition);
    }
    else if (elementDefinition instanceof PyTargetExpression) {
      PyTargetExpression target = (PyTargetExpression)elementDefinition;
      if (isAttribute()) {
        final String type = PyUtil.isInstanceAttribute(target) ? "Instance attribute " : "Class attribute ";
        myProlog
          .addItem(type)
          .addWith(TagBold, $().addWith(TagCode, $(elementDefinition.getName())))
          .addItem(" of class ")
          .addItem(PyDocumentationLink.toContainingClass(WRAP_IN_CODE.apply(target.getContainingClass().getName())));
      }
      myBody.add(PythonDocumentationProvider.describeTarget(target, context));
    }
  }

  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  @NotNull
  private PsiElement resolveToDocStringOwner(@NotNull TypeEvalContext context) {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression && ((PyTargetExpression)myElement).getDocStringValue() == null) {
      final PyExpression assignedValue = ((PyTargetExpression)myElement).findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression) {
        final PsiElement resolved = resolveWithoutImplicits((PyReferenceExpression)assignedValue, context);
        if (resolved instanceof PyDocStringOwner) {
          mySectionsMap.get(PyBundle.message("QDOC.assigned.to")).addWith(TagCode, $(((PyTargetExpression)myElement).getName()));
          return resolved;
        }
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
          final String ancestorName = ancestor.getName();
          final String ancestorQualifiedName = ancestor.getQualifiedName();
          final TypeEvalContext context = TypeEvalContext.userInitiated(pyFunction.getProject(), pyFunction.getContainingFile());

          final String ancestorLink = pyClass == ancestor
                                      ? PyDocumentationLink.toContainingClass(ancestorName)
                                      : ancestorName != null && ancestorQualifiedName != null
                                        ? PyDocumentationLink.toPossibleClass(ancestorName, ancestorQualifiedName, pyClass, context)
                                        : null;
          if (ancestorLink != null) {
            final ChainIterable<String> link = mySectionsMap.get(PyBundle.message("QDOC.documentation.is.copied.from"));
            link.addWith(TagCode, isFromClass ? $(ancestorLink) : $(ancestorLink).addItem("." + methodName));
          }
          myContent.add(formatDocString(pyFunction, inheritedDoc));
          return;
        }
      }
    }

    // above could have not worked because inheritance is not searched down to 'object'.
    // for well-known methods, copy built-in doc string.
    // TODO: also handle predefined __xxx__ that are not part of 'object'.
    if (PyNames.UNDERSCORED_ATTRIBUTES.contains(methodName)) {
      addPredefinedMethodDoc(pyFunction, methodName);
    }
  }

  private void addPredefinedMethodDoc(@NotNull PyFunction fun, @NotNull String methodName) {
    final PyClassType objectType = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objectType != null) {
      final PyClass objectClass = objectType.getPyClass();
      final PyFunction predefinedMethod = objectClass.findMethodByName(methodName, false, null);
      if (predefinedMethod != null) {
        final PyStringLiteralExpression predefinedDocstring = getEffectiveDocStringExpression(predefinedMethod);
        final String predefinedDoc = predefinedDocstring != null ? predefinedDocstring.getStringValue() : null;
        if (predefinedDoc != null && predefinedDoc.length() > 1) { // only a real-looking doc string counts
          mySectionsMap.get(PyBundle.message("QDOC.documentation.is.copied.from")).addItem("built-in description");
          myContent.add(formatDocString(fun, predefinedDoc));
        }
      }
    }
  }

  @NotNull
  private static ChainIterable<String> formatDocString(@NotNull PsiElement element, @NotNull String docstring) {
    final Project project = element.getProject();

    final List<String> formatted = PyStructuredDocstringFormatter.formatDocstring(element, docstring);
    if (formatted != null) {
      return new ChainIterable<>(formatted);
    }

    boolean isFirstLine;
    final String[] lines = removeCommonIndentation(docstring);

    final ChainIterable<String> result = new ChainIterable<>();
    // reconstruct back, dropping first empty fragment as needed
    isFirstLine = true;
    final int tabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(PythonFileType.INSTANCE);
    for (String line : lines) {
      if (isFirstLine && ourSpacesPattern.matcher(line).matches()) continue; // ignore all initial whitespace
      if (isFirstLine) {
        isFirstLine = false;
      }
      else {
        result.addItem(BR);
      }
      int leadingTabs = 0;
      while (leadingTabs < line.length() && line.charAt(leadingTabs) == '\t') {
        leadingTabs++;
      }
      if (leadingTabs > 0) {
        line = StringUtil.repeatSymbol(' ', tabSize * leadingTabs) + line.substring(leadingTabs);
      }
      result.addItem(combUp(line));
    }
    return result;
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
