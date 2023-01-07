// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.google.common.collect.Lists;
import com.intellij.lang.ASTNode;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.FactoryMap;
import com.jetbrains.python.*;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;
import static com.jetbrains.python.psi.PyUtil.as;

public class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private final ChainIterable<String> myProlog;      // definition header
  private final ChainIterable<String> myBody;        // definition main part
  private final ChainIterable<String> myContent;     // sequence for doc string
  private final ChainIterable<String> mySections;

  private final Map<String, ChainIterable<String>> mySectionsMap = FactoryMap.createMap(item -> new ChainIterable<>(), LinkedHashMap::new);
  private final TypeEvalContext myContext;

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");

  public PyDocumentationBuilder(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myProlog = new ChainIterable<>();
    myBody = new ChainIterable<>();
    myContent = new ChainIterable<>();
    mySections = new ChainIterable<>();
    myContext = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
  }

  @Nullable
  public @Nls String build() {
    final PsiElement outerElement = myOriginalElement != null ? myOriginalElement.getParent() : null;

    PsiElement elementDefinition = resolveToDocStringOwner();
    final PsiElement propertyDefinition = buildFromProperty(elementDefinition, outerElement);
    boolean isProperty = false;
    if (propertyDefinition != null) {
      isProperty = true;
      elementDefinition = propertyDefinition;
    }

    if (elementDefinition instanceof PyDocStringOwner) {
      buildFromDocstring(((PyDocStringOwner)elementDefinition), isProperty);
    }
    else if (elementDefinition instanceof PyNamedParameter) {
      buildFromParameter((PyNamedParameter)elementDefinition);
    }

    final ASTNode node = elementDefinition.getNode();
    if (node != null) {
      final var elementType = node.getElementType();
      if (PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(elementType)) {
        buildForKeyword(getDocFileNameForKeywordElement(elementDefinition, elementType));
      }
    }

    if (!mySectionsMap.isEmpty()) {
      mySections.addItem(DocumentationMarkup.SECTIONS_START);
      final List<String> firstSections = Lists.newArrayList(PyPsiBundle.message("QDOC.attributes"),
                                                            PyPsiBundle.message("QDOC.params"),
                                                            PyPsiBundle.message("QDOC.keyword.args"),
                                                            PyPsiBundle.message("QDOC.returns"),
                                                            PyPsiBundle.message("QDOC.raises"));
      firstSections.retainAll(mySectionsMap.keySet());

      final ArrayList<String> remainingSections = new ArrayList<>(mySectionsMap.keySet());
      remainingSections.removeAll(firstSections);

      // FactoryMap's entrySet() returns pairs without particular order even for LinkedHashMap
      for (String header : ContainerUtil.concat(firstSections, remainingSections)) {
        mySections.addItem(DocumentationMarkup.SECTION_HEADER_START);
        mySections.addItem(header);
        mySections.addItem(DocumentationMarkup.SECTION_SEPARATOR);
        mySections.add(mySectionsMap.get(header));
        mySections.addItem(DocumentationMarkup.SECTION_END);
      }
      mySections.addItem(DocumentationMarkup.SECTIONS_END);
    }

    if (myBody.isEmpty() && myContent.isEmpty()) {
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
      result.add(mySections); // pre-assemble; then add stuff to individual cats as needed
      return wrapInTag("html", wrapInTag("body", result)).toString();
    }
  }

  private static String getDocFileNameForKeywordElement(PsiElement element, IElementType elementType) {
    final var parentStatement = PsiTreeUtil.getParentOfType(element, PyStatement.class);

    if (parentStatement == null) {
      return element.getText();
    }

    if (elementType == PyTokenTypes.FROM_KEYWORD) {
      // We want to show yield doc in 'yield from ...' expressions when hover to 'from',
      // but there is no particular PyStatement for 'yield' keyword, therefore we make such a check.
      if (parentStatement.getFirstChild() instanceof PyYieldExpression) {
        return PyNames.YIELD;
      }
      else if (parentStatement instanceof PyRaiseStatement) {
        return PyNames.RAISE;
      }
    }
    else if (elementType == PyTokenTypes.AS_KEYWORD) {
      if (parentStatement instanceof PyWithStatement) {
        return PyNames.WITH;
      }
      else if (parentStatement instanceof PyTryExceptStatement) {
        return PyNames.EXCEPT;
      }
    }
    else if (elementType == PyTokenTypes.ELSE_KEYWORD) {
      if (parentStatement instanceof PyWhileStatement) {
        return PyNames.WHILE;
      }
      else if (parentStatement instanceof PyForStatement) {
        return PyNames.FOR;
      }
      else if (parentStatement instanceof PyTryExceptStatement) {
        return PyNames.TRY;
      }
    }
    else if (elementType == PyTokenTypes.IN_KEYWORD && parentStatement instanceof PyForStatement) {
      return PyNames.FOR;
    }
    return element.getText();
  }

  private void buildForKeyword(@NotNull String name) {
    try {
      try (FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/python_keywords/" + name))) {
        final String text = FileUtil.loadTextAndClose(reader);
        myContent.addItem(StringUtil.convertLineSeparators(text, "\n"));
      }
    }
    catch (IOException ignored) {
    }
  }

  private void buildFromParameter(@NotNull PyNamedParameter parameter) {
    final PyFunction func = PsiTreeUtil.getParentOfType(parameter, PyFunction.class, true, PyLambdaExpression.class);
    final String link = func != null ? getLinkToFunction(func, true) : StringUtil.escapeXmlEntities(PyNames.UNNAMED_ELEMENT);
    if (link != null) {
      myProlog
        .addItem("Parameter ")
        .addWith(TagBold, $(parameter.getName()))
        .addItem(" of ")
        .addItem(link);
    }

    if (func != null) {
      final PyClass containingClass = func.getContainingClass();
      final PyStringLiteralExpression functionDocstring = getEffectiveDocStringExpression(func);
      final PyStringLiteralExpression docString = functionDocstring == null && containingClass != null ?
                                                  addFunctionInheritedDocString(func, containingClass) :
                                                  functionDocstring;
      if (docString != null) {
        final StructuredDocString structuredDocString = DocStringUtil.parse(docString.getStringValue());
        final String description = structuredDocString.getParamDescription(parameter.getName());
        if (StringUtil.isNotEmpty(description)) {
          myContent.add($(description));
        }
      }
    }
    myBody.add(PythonDocumentationProvider.describeParameter(parameter, myContext));
  }

  @Nullable
  private PsiElement buildFromProperty(@NotNull PsiElement elementDefinition, @Nullable PsiElement outerElement) {
    if (myOriginalElement == null) {
      return null;
    }
    final String elementName = myOriginalElement.getText();
    if (!PyNames.isIdentifier(elementName)) {
      return null;
    }
    if (!(outerElement instanceof PyQualifiedExpression)) {
      return null;
    }
    final PyExpression qualifier = ((PyQualifiedExpression)outerElement).getQualifier();
    if (qualifier == null) {
      return null;
    }
    final PyType type = myContext.getType(qualifier);
    if (!(type instanceof PyClassType)) {
      return null;
    }
    final PyClass cls = ((PyClassType)type).getPyClass();
    final Property property = cls.findProperty(elementName, true, null);
    if (property == null) {
      return null;
    }

    final AccessDirection direction = AccessDirection.of((PyElement)outerElement);
    final Maybe<PyCallable> accessor = property.getByDirection(direction);
    final String link = getLinkToClass(cls, true);
    if (link != null) {
      myProlog.addItem("Property ")
              .addWith(TagBold, $(elementName))
              .addItem(" of ")
              .addItem(link);
    }

    // Choose appropriate docstring
    String docstring = null;
    if (property.getDoc() != null) {
      docstring = property.getDoc();
    }
    if (docstring == null) {
      final PyFunction accessorFunc = as(accessor.valueOrNull(), PyFunction.class);
      if (accessorFunc != null) {
        final PyStringLiteralExpression accessorDocstring = getEffectiveDocStringExpression(accessorFunc);
        if (accessorDocstring != null) {
          docstring = accessorDocstring.getStringValue();
        }
      }
    }
    if (docstring == null && direction != AccessDirection.READ) {
      final PyFunction getter = as(property.getGetter().valueOrNull(), PyFunction.class);
      if (getter != null) {
        // not in getter, getter's doc comment may be useful
        final PyStringLiteralExpression getterDocstring = getEffectiveDocStringExpression(getter);
        if (getterDocstring != null) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.documentation.is.copied.from")).addItem("property getter");
          docstring = getterDocstring.getStringValue();
        }
      }
    }
    if (docstring != null) {
      myContent.add(formatDocString(elementDefinition, docstring));
    }
    final String accessorKind = getAccessorKind(direction);
    mySectionsMap.get(PyPsiBundle.message("QDOC.accessor.kind"))
                 .addItem(accessorKind)
                 .addItem(accessor.valueOrNull() == null ? " (not defined)" : "");

    // Choose appropriate definition to display
    if (accessor.valueOrNull() != null) {
      return accessor.value();
    }
    else if (property.getGetter().valueOrNull() != null) {
      return property.getGetter().value();
    }
    else {
      return property.getDefinitionSite();
    }
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
    PyStringLiteralExpression docStringExpression = getEffectiveDocStringExpression(elementDefinition);

    if (PyUtil.isTopLevel(elementDefinition)) {
      final PsiFile containing = elementDefinition.getContainingFile();
      if (containing instanceof PyFile) {
        final String link = getLinkToModule((PyFile)containing);
        if (link != null) {
          myProlog.addItem(link);
        }
      }
    }

    if (elementDefinition instanceof PyClass pyClass) {
      myBody.add(PythonDocumentationProvider.describeDecorators(pyClass, WRAP_IN_ITALIC, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, BR, BR));
      myBody.add(
        PythonDocumentationProvider.describeClass(pyClass, WRAP_IN_BOLD, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, false, true, myContext));
      docStringExpression = addClassDocumentation(docStringExpression, pyClass);
    }
    else if (elementDefinition instanceof PyFunction pyFunction) {
      myBody.add(PythonDocumentationProvider.describeDecorators(pyFunction, WRAP_IN_ITALIC, ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES, BR, BR));
      myBody.add(PythonDocumentationProvider.describeFunction(pyFunction, myContext, false));
      final PyClass pyClass = pyFunction.getContainingClass();
      if (!isProperty && pyClass != null) {
        final String link = getLinkToClass(pyClass, true);
        if (link != null) {
          myProlog.addItem(link);
        }
      }
      docStringExpression = addFunctionDocumentation(docStringExpression, pyFunction, pyClass, isProperty);
    }
    else if (elementDefinition instanceof PyFile pyFile) {
      addModulePath(pyFile);
    }
    else if (elementDefinition instanceof PyTargetExpression target) {
      addTargetDocumentation(target, isProperty);
    }

    if (docStringExpression != null && !isProperty) {
      myContent.add(formatDocString(myElement, docStringExpression.getStringValue()));
    }
  }

  private void addTargetDocumentation(@NotNull PyTargetExpression target, boolean isProperty) {
    if (isAttribute() && !isProperty) {
      final PyClass containingClass = target.getContainingClass();
      @SuppressWarnings("ConstantConditions") final String link = getLinkToClass(containingClass, true);
      if (link != null) {
        myProlog.addItem(PyUtil.isInstanceAttribute(target) ? "Instance attribute " : "Class attribute ")
          .addWith(TagBold, $(target.getName()))
          .addItem(" of ")
          .addItem(link);
      }
      // if there is no separate doc for attribute we will try to take it from class doc
      if (getEffectiveDocStringExpression(target) == null) {
        final PyStringLiteralExpression docString = getEffectiveDocStringExpression(containingClass);
        if (docString != null) {
          final StructuredDocString structuredDocString = DocStringUtil.parse(docString.getStringValue());
          final String description = structuredDocString.getAttributeDescription(target.getName());
          if (StringUtil.isNotEmpty(description)) {
            myContent.add($(description));
          }
        }
      }
    }
    myBody.add(PythonDocumentationProvider.describeTarget(target, myContext));
  }

  @Nullable
  private PyStringLiteralExpression addFunctionDocumentation(@Nullable PyStringLiteralExpression docStringExpression,
                                                             @NotNull PyFunction pyFunction,
                                                             @Nullable PyClass pyClass,
                                                             boolean isProperty) {
    // if we have containing class, but don't have a function doc
    if (docStringExpression == null && pyClass != null) {
      if (!isProperty) {
        // add docstring from the parent class or function
        docStringExpression = addFunctionInheritedDocString(pyFunction, pyClass);
      }
      if (PyUtil.isInitOrNewMethod(pyFunction)) {
        final PyStringLiteralExpression classDocstring = getEffectiveDocStringExpression(pyClass);
        if (classDocstring != null) {
          // we should add attributes section from class doc because it won't be added with function sections in the end
          addAttributesSection(classDocstring, pyClass);
        }
      }
    }
    if (docStringExpression != null) {
      addFunctionSpecificSections(docStringExpression, pyFunction);
    }
    return docStringExpression;
  }

  @Nullable
  private PyStringLiteralExpression addClassDocumentation(@Nullable PyStringLiteralExpression docStringExpression,
                                                          @NotNull PyClass pyClass) {
    final PyFunction init = pyClass.findMethodByName(PyNames.INIT, false, myContext);
    if (docStringExpression != null) {
      addAttributesSection(docStringExpression, pyClass);
      if (init != null) {
        // add init parameters described in the class doc
        addFunctionSpecificSections(docStringExpression, init);
      }
      return docStringExpression;
    }
    // if class doesn't have any doc add init doc without other sections
    if (init != null) {
      return getEffectiveDocStringExpression(init);
    }
    return null;
  }

  private void addAttributesSection(@NotNull PyStringLiteralExpression docstring, @NotNull PyClass cls) {
    final StructuredDocString structured = DocStringUtil.parseDocString(docstring);
    final List<String> documentedAttributes = structured.getAttributes();

    final String allAttributes = StreamEx.of(documentedAttributes)
      .map(name -> {
        final String description = structured.getAttributeDescription(name);
        return "<p><code>" + name + "</code> &ndash; " + Strings.notNullize(description) + "</p>";
      })
      .joining();

    if (!allAttributes.isEmpty()) {
      mySectionsMap.get(PyPsiBundle.message("QDOC.attributes")).addItem(allAttributes);
    }
  }

  private void addFunctionSpecificSections(@NotNull PyStringLiteralExpression docstring, @NotNull PyFunction function) {
    final StructuredDocString structured = DocStringUtil.parseDocString(docstring);

    final List<PyCallableParameter> parameters = function.getParameters(myContext);
    final List<String> actualNames = ContainerUtil.mapNotNull(parameters, PyCallableParameter::getName);
    // Retain the actual order of parameters
    final String paramList = StreamEx.of(actualNames)
      .filter(name -> structured.getParamDescription(name) != null)
      .map(name -> {
        final String description = structured.getParamDescription(name);
        return "<p><code>" + name + "</code> &ndash; " + Strings.notNullize(description) + "</p>";
      })
      .joining();

    if (!paramList.isEmpty()) {
      mySectionsMap.get(PyPsiBundle.message("QDOC.params")).addItem(paramList);
    }

    final List<String> allKeywordArgs = structured.getKeywordArguments();
    if (!ContainerUtil.exists(parameters, PyCallableParameter::isKeywordContainer)) {
      allKeywordArgs.retainAll(new HashSet<>(actualNames));
    }
    final String keywordArgsList = StreamEx.of(allKeywordArgs)
      .map(name -> {
        final String description = structured.getKeywordArgumentDescription(name);
        return "<p><code>" + name + "</code> &ndash; " + StringUtil.notNullize(description) + "</p>";
      })
      .joining();
    if (!keywordArgsList.isEmpty()) {
      mySectionsMap.get(PyPsiBundle.message("QDOC.keyword.args")).addItem(keywordArgsList);
    }

    final String returnDescription = structured.getReturnDescription();
    if (returnDescription != null && !mySectionsMap.containsKey(PyPsiBundle.message("QDOC.returns"))) {
      mySectionsMap.get(PyPsiBundle.message("QDOC.returns")).addItem(Strings.notNullize(returnDescription));
    }

    final String exceptionList = StreamEx.of(structured.getRaisedExceptions())
                                          .map(name -> {
                                            final String description = structured.getRaisedExceptionDescription(name);
                                            return "<p><code>" + name + "</code>" +
                                                   (StringUtil.isNotEmpty(description) ? " &ndash; " + description : "") + "</p>";
                                          })
                                          .joining();

    if (!exceptionList.isEmpty()) {
      mySectionsMap.get(PyPsiBundle.message("QDOC.raises")).addItem(exceptionList);
    }
  }

  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  @NotNull
  private PsiElement resolveToDocStringOwner() {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression && ((PyTargetExpression)myElement).getDocStringValue() == null) {
      final PyExpression assignedValue = ((PyTargetExpression)myElement).findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression) {
        final PsiElement resolved = resolveWithoutImplicits((PyReferenceExpression)assignedValue);
        if (resolved instanceof PyDocStringOwner) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.assigned.to")).addWith(TagCode, $(((PyTargetExpression)myElement).getName()));
          return resolved;
        }
      }
    }
    // Reference expression can be passed as the target element in Python console
    if (myElement instanceof PyReferenceExpression) {
      final PsiElement resolved = resolveWithoutImplicits((PyReferenceExpression)myElement);
      if (resolved != null) {
        return resolved;
      }
    }
    return myElement;
  }

  @Nullable
  private PsiElement resolveWithoutImplicits(@NotNull PyReferenceExpression element) {
    final PyResolveContext resolveContext = PyResolveContext.defaultContext(myContext);
    final QualifiedResolveResult resolveResult = element.followAssignmentsChain(resolveContext);
    return resolveResult.isImplicit() ? null : resolveResult.getElement();
  }

  @Nullable
  private PyStringLiteralExpression addFunctionInheritedDocString(@NotNull PyFunction pyFunction, @NotNull PyClass pyClass) {
    final String methodName = pyFunction.getName();
    if (methodName == null) {
      return null;
    }
    final boolean isConstructor = PyUtil.isInitOrNewMethod(pyFunction);
    Iterable<PyClass> classes = pyClass.getAncestorClasses(myContext);
    if (isConstructor) {
      // look at our own class again and maybe inherit class's doc
      classes = new ChainIterable<>(pyClass).add(classes);
    }
    for (PyClass ancestor : classes) {
      PyStringLiteralExpression docstringElement = null;
      PyFunction inherited = null;
      boolean isFromClass = false;
      if (isConstructor) {
        docstringElement = getEffectiveDocStringExpression(ancestor);
        isFromClass = docstringElement != null;
      }
      if (!isFromClass) {
        inherited = ancestor.findMethodByName(methodName, false, null);
        if (inherited != null) {
          docstringElement = getEffectiveDocStringExpression(inherited);
        }
      }
      if (docstringElement != null && !docstringElement.getStringValue().isEmpty()) {
        final String ancestorLink = isFromClass ? getLinkToClass(ancestor, false) : getLinkToFunction(inherited, false);
        if (ancestorLink != null) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.documentation.is.copied.from")).addWith(TagCode, $(ancestorLink));
        }
        return docstringElement;
      }
    }

    // above could have not worked because inheritance is not searched down to 'object'.
    // for well-known methods, copy built-in doc string.
    // TODO: also handle predefined __xxx__ that are not part of 'object'.
    if (PyNames.UNDERSCORED_ATTRIBUTES.contains(methodName)) {
      return addPredefinedMethodDoc(pyFunction, methodName);
    }
    return null;
  }

  @Nullable
  private PyStringLiteralExpression addPredefinedMethodDoc(@NotNull PyFunction fun, @NotNull String methodName) {
    final PyClassType objectType = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objectType != null) {
      final PyClass objectClass = objectType.getPyClass();
      final PyFunction predefinedMethod = objectClass.findMethodByName(methodName, false, null);
      if (predefinedMethod != null) {
        final PyStringLiteralExpression predefinedDocstring = getEffectiveDocStringExpression(predefinedMethod);
        final String predefinedDoc = predefinedDocstring != null ? predefinedDocstring.getStringValue() : null;
        if (predefinedDoc != null && !predefinedDoc.isEmpty()) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.documentation.is.copied.from")).addItem("built-in description");
          myContent.add(formatDocString(fun, predefinedDoc));
        }
        return predefinedDocstring;
      }
    }
    return null;
  }

  @NotNull
  private static ChainIterable<String> formatDocString(@NotNull PsiElement element, @NotNull String docstring) {

    final List<String> formatted = PyStructuredDocstringFormatter.formatDocstring(element, docstring);
    if (formatted != null) {
      return new ChainIterable<>(formatted);
    }

    final List<String> origLines = LineTokenizer.tokenizeIntoList(docstring.trim(), false, false);
    final List<String> updatedLines = StreamEx.of(PyIndentUtil.removeCommonIndent(origLines, true))
                                              .takeWhile(line -> !line.startsWith(">>>")) //TODO: PyConsoleUtil.ORDINARY_PROMPT
                                              .toList();
    final ChainIterable<String> result = new ChainIterable<>();
    // reconstruct back, dropping first empty fragment as needed
    boolean isFirstLine = true;
    final int tabSize = PythonCodeStyleService.getInstance().getTabSize(element.getContainingFile());
    for (String line : updatedLines) {
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

  private void addModulePath(@NotNull PyFile followed) {
    // what to prepend to a module description?
    final VirtualFile file = followed.getVirtualFile();
    if (file == null) {
      myProlog.addWith(TagSmall, $(PyPsiBundle.message("QDOC.module.path.unknown")));
    }
    else {
      final QualifiedName name = QualifiedNameFinder.findShortestImportableQName(followed);
      if (name != null) {
        myProlog.add($(PyUtil.isPackage(followed) ? "Package " : "Module "))
          .addWith(TagBold, $(ObjectUtils.chooseNotNull(QualifiedNameFinder.canonizeQualifiedName(followed, name, null), name).toString()));
      }
      else {
        final String path = file.getPath();
        myProlog.addWith(TagSpan.withAttribute("path", path), $(path));
      }
    }
  }

  @Nullable
  private static String getLinkToModule(@NotNull PyFile module) {
    final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(module, null);
    if (name != null) {
      return PyDocumentationLink.toModule(name.toString(), name.toString());
    }
    final VirtualFile vFile = module.getVirtualFile();
    return vFile != null ? vFile.getPath() : null;
  }

  @Nullable
  private String getLinkToClass(@NotNull PyClass pyClass, boolean preferQualifiedName) {
    final String qualifiedName = pyClass.getQualifiedName();
    final String shortName = pyClass.getName();

    final String linkText = preferQualifiedName && qualifiedName != null ? qualifiedName : shortName;
    if (linkText == null) {
      return null;
    }

    if (qualifiedName != null) {
      return PyDocumentationLink.toPossibleClass(linkText, qualifiedName, pyClass, myContext);
    }
    else if (PsiTreeUtil.getParentOfType(myElement, PyClass.class, false) == pyClass) {
      return PyDocumentationLink.toContainingClass(linkText);
    }
    return linkText;
  }

  @Nullable
  private static String getLinkToFunction(@NotNull PyFunction function, boolean preferQualifiedName) {
    final String qualifiedName = function.getQualifiedName();
    final PyClass pyClass = function.getContainingClass();
    // Preserve name of a containing class even if the whole qualified name can't be constructed
    final String shortName = pyClass == null ? function.getName() : pyClass.getName() + "." + function.getName();

    final String linkText = preferQualifiedName && qualifiedName != null ? qualifiedName : shortName;
    if (linkText == null || function.getName() == null || (pyClass != null && pyClass.getName() == null)) {
      return null;
    }

    if (qualifiedName != null) {
      return PyDocumentationLink.toFunction(linkText, function);
    }
    return linkText;
  }

  @Nullable
  static PyStringLiteralExpression getEffectiveDocStringExpression(@NotNull PyDocStringOwner owner) {
    final PyStringLiteralExpression expression = owner.getDocStringExpression();
    if (expression != null) {
      return expression;
    }
    final PsiElement original = PyiUtil.getOriginalElement(owner);
    final PyDocStringOwner originalOwner = as(original, PyDocStringOwner.class);
    return originalOwner != null ? originalOwner.getDocStringExpression() : null;
  }
}
