// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.icons.AllIcons;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.*;
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
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.codeInsight.decorator.PyFunctoolsWrapsDecoratedFunctionTypeProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.*;

import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

import static com.intellij.lang.documentation.DocumentationMarkup.*;
import static com.jetbrains.python.psi.PyUtil.as;

public final class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private final HtmlBuilder myProlog;      // definition header (link to class or module)
  private final HtmlBuilder myBody;        // definition main part (signature / element description)
  private final HtmlBuilder myContent;     // main part of docstring
  private final HtmlBuilder mySections;
  private final List<FormatterDocFragment> myFormatterFragments;
  private final Map<String, HtmlBuilder> mySectionsMap = FactoryMap.createMap(item -> new HtmlBuilder(), LinkedHashMap::new);
  private final TypeEvalContext myContext;

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");

  public PyDocumentationBuilder(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myProlog = new HtmlBuilder();
    myBody = new HtmlBuilder();
    myContent = new HtmlBuilder();
    mySections = new HtmlBuilder();
    myFormatterFragments = new ArrayList<>();
    myContext = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
  }

  public @Nullable @Nls String build() {
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
    else if (elementDefinition instanceof PyTypeParameter typeParameter) {
      buildFromTypeParameter(typeParameter);
    }
    else if (elementDefinition instanceof PyTypeAliasStatement typeAliasStatement) {
      buildFromTypeAliasStatement(typeAliasStatement);
    }

    final ASTNode node = elementDefinition.getNode();
    if (node != null) {
      final var elementType = node.getElementType();
      if (PythonDialectsTokenSetProvider.getInstance().getKeywordTokens().contains(elementType)) {
        buildForKeyword(getDocFileNameForKeywordElement(elementDefinition, elementType));
      }
    }

    if (!mySectionsMap.isEmpty()) {
      final List<String> canonicalSectionOrder =
        List.of(PyPsiBundle.message("QDOC.attributes"),
                PyPsiBundle.message("QDOC.params"),
                PyPsiBundle.message("QDOC.keyword.args"),
                PyPsiBundle.message("QDOC.returns"),
                PyPsiBundle.message("QDOC.raises"));

      final List<String> topSections = new ArrayList<>(canonicalSectionOrder);
      topSections.retainAll(mySectionsMap.keySet());

      final ArrayList<String> remainingSections = new ArrayList<>(mySectionsMap.keySet());
      remainingSections.removeAll(topSections);

      // FactoryMap's entrySet() returns pairs without particular order even for LinkedHashMap
      for (@NlsSafe String header : ContainerUtil.concat(topSections, remainingSections)) {
        mySections.append(HtmlChunk.tag("tr").children(
          HtmlChunk.text(header).wrapWith(SECTION_HEADER_CELL),
          mySectionsMap.get(header).wrapWith(SECTION_CONTENT_CELL)
        ));
      }
    }

    for (PythonDocumentationQuickInfoProvider point : PythonDocumentationQuickInfoProvider.EP_NAME.getExtensionList()) {
      final String info = point.getHoverAdditionalQuickInfo(myContext, outerElement);
      if (info != null) {
        myBody.br();
        myBody.append(info);
        break;
      }
    }


    if (myBody.isEmpty() && myContent.isEmpty()) {
      return null; // got nothing substantial to say!
    }
    else {
      final HtmlBuilder result = new HtmlBuilder();
      final HtmlBuilder definitionBuilder = new HtmlBuilder();

      if (!myProlog.isEmpty()) {
        definitionBuilder.append(myProlog);
      }
      if (!myBody.isEmpty()) {
        definitionBuilder.append(myBody.wrapWith(PRE_ELEMENT).wrapWith(DEFINITION_ELEMENT));
      }
      result.append(definitionBuilder);
      if (!myContent.isEmpty()) {
        result.append(myContent.wrapWith(CONTENT_ELEMENT));
      }
      if (!mySectionsMap.isEmpty()) {
        result.append(mySections.wrapWith(SECTIONS_TABLE));
      }
      return result.wrapWithHtmlBody().toString();
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
      try (FileReader reader = new FileReader(PythonHelpersLocator.findPathStringInHelpers("/tools/python_keywords/" + name),
                                              StandardCharsets.UTF_8)) {
        final String text = FileUtil.loadTextAndClose(reader);
        final String converted = StringUtil.convertLineSeparators(text, "\n");
        myContent.appendRaw(converted);
      }
    }
    catch (IOException ignored) {
    }
  }

  private void buildFromParameter(@NotNull PyNamedParameter parameter) {
    final PyFunction func = PsiTreeUtil.getParentOfType(parameter, PyFunction.class, true, PyLambdaExpression.class);
    final HtmlChunk link = func != null ? getLinkToFunction(func, true) : HtmlChunk.text(PyNames.UNNAMED_ELEMENT);
    final String parameterName = parameter.getName();
    if (link != null && parameterName != null) {
      myBody.appendRaw(PyPsiBundle.message("QDOC.parameter.name.of.link", HtmlChunk.text(parameterName).bold(), link)).br();
    }

    if (func != null) {
      final PyClass containingClass = func.getContainingClass();
      final PyStringLiteralExpression functionDocstring = getEffectiveDocStringExpression(func);
      final PyStringLiteralExpression docString =
        functionDocstring == null && containingClass != null ? addFunctionInheritedDocString(func, containingClass) : functionDocstring;
      if (docString != null) {
        final StructuredDocString structuredDocString = DocStringUtil.parse(docString.getStringValue());
        final String description = structuredDocString.getParamDescription(parameter.getName());
        if (StringUtil.isNotEmpty(description)) {
          myContent.append(runFormatterService(description));
        }
      }
    }
    myBody.append(PythonDocumentationProvider.describeParameter(parameter, myContext));
  }

  private void buildFromTypeParameter(@NotNull PyTypeParameter typeParameter) {
    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(typeParameter);
    HtmlChunk link = null;
    String typeParamName = typeParameter.getName();
    if (scopeOwner instanceof PyFunction pyFunction) {
      link = getLinkToFunction(pyFunction, true);
    }
    else if (scopeOwner instanceof PyClass pyClass) {
      link = getLinkToClass(pyClass, true);
    }
    else if (scopeOwner instanceof PyTypeAliasStatement typeAliasStatement) {
      link = getLinkToTypeAliasStatement(typeAliasStatement);
    }
    if (link != null && typeParamName != null) {
      myBody.appendRaw(PyPsiBundle.message("QDOC.type.parameter.name.of.link", HtmlChunk.text(typeParamName).bold(), link)).br();
      myBody.append(PythonDocumentationProvider.describeTypeParameter(typeParameter, true, myContext));
    }
  }

  private void buildFromTypeAliasStatement(@NotNull PyTypeAliasStatement typeAliasStatement) {
    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(typeAliasStatement);
    HtmlChunk link = null;
    String typeParamName = typeAliasStatement.getName();
    if (scopeOwner instanceof PyFunction pyFunction) {
      link = getLinkToFunction(pyFunction, true);
    }
    else if (scopeOwner instanceof PyClass pyClass) {
      link = getLinkToClass(pyClass, true);
    }
    else if (scopeOwner instanceof PyFile pyFile) {
      link = getLinkToModule(pyFile);
    }
    if (link != null && typeParamName != null) {
      myBody.appendRaw(PyPsiBundle.message("QDOC.type.alias.statement.name.of.link", HtmlChunk.text(typeParamName).bold(), link)).br();
      myBody.append(PythonDocumentationProvider.describeTypeAlias(typeAliasStatement, myContext));
    }
  }

  private @NotNull HtmlChunk runFormatterService(@NotNull @Nls String description) {
    final DocstringFormatterRequest output =
      PyStructuredDocstringFormatter.formatDocstring(myElement, new DocstringFormatterRequest(description, Collections.emptyList()),
                                                     List.of(PyStructuredDocstringFormatter.FORMATTER_FRAGMENTS_FLAG));
    return output != null ? HtmlChunk.raw(output.getBody()) : HtmlChunk.text(description);
  }

  private @Nullable PsiElement buildFromProperty(@NotNull PsiElement elementDefinition, @Nullable PsiElement outerElement) {
    if (myOriginalElement == null) {
      return null;
    }
    final String propertyName = myOriginalElement.getText();
    if (!PyNames.isIdentifier(propertyName)) {
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
    final Property property = cls.findProperty(propertyName, true, null);
    if (property == null) {
      return null;
    }

    final AccessDirection direction = AccessDirection.of((PyElement)outerElement);
    final Maybe<PyCallable> accessor = property.getByDirection(direction);
    final HtmlChunk link = getLinkToClass(cls, true);
    if (link != null) {
      myBody.appendRaw(PyPsiBundle.message("QDOC.property.name.of.link", HtmlChunk.text(propertyName).bold(), link)).br();
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
    final Maybe<PyCallable> propertyGetter = property.getGetter();
    if (docstring == null && direction != AccessDirection.READ) {
      final PyFunction getter = as(propertyGetter.valueOrNull(), PyFunction.class);
      if (getter != null) {
        // not in getter, getter's doc comment may be useful
        final PyStringLiteralExpression getterDocstring = getEffectiveDocStringExpression(getter);
        if (getterDocstring != null) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.copied.from")).append(PyPsiBundle.message("QDOC.property.getter"));
          docstring = getterDocstring.getStringValue();
        }
      }
    }
    if (docstring != null) {
      myContent.append(safeRunFormatterService(elementDefinition, docstring));
    }
    mySectionsMap.get(PyPsiBundle.message("QDOC.accessor.kind")).append(getAccessorKind(direction))
      .append(accessor.valueOrNull() == null ? " " + PyPsiBundle.message("QDOC.not.defined.in.parentheses") : "");

    // Choose appropriate definition to display
    if (accessor.valueOrNull() != null) {
      return accessor.value();
    }
    else if (propertyGetter.valueOrNull() != null) {
      return propertyGetter.value();
    }
    else {
      return property.getDefinitionSite();
    }
  }

  private static @NotNull String getAccessorKind(final @NotNull AccessDirection dir) {
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

  private void buildFromDocstring(final @NotNull PyDocStringOwner elementDefinition, boolean isProperty) {
    final PyStringLiteralExpression ownDocstring = getEffectiveDocStringExpression(elementDefinition);
    final PyStringLiteralExpression effectiveDocstring = modifyDocStringByOwnerType(ownDocstring, elementDefinition, isProperty);

    if (PyUtil.isTopLevel(elementDefinition)) {
      final PsiFile containing = ObjectUtils.chooseNotNull(PyiUtil.getOriginalElement(elementDefinition), 
                                                           elementDefinition).getContainingFile();
      if (containing instanceof PyFile) {
        final HtmlChunk linkToModule = getLinkToModule((PyFile)containing);
        if (linkToModule != null) {
          myProlog.append(BOTTOM_ELEMENT
                            .children(
                              HtmlChunk.icon("AllIcons.Nodes.Package", AllIcons.Nodes.Package),
                              HtmlChunk.nbsp(),
                              linkToModule.code()
                            ));
        }
      }
    }

    if (elementDefinition instanceof PyClass pyClass) {
      myBody.append(PythonDocumentationProvider.describeDecorators(pyClass, HtmlChunk.br()));
      myBody.append(PythonDocumentationProvider.describeClass(pyClass, myContext));
      if (effectiveDocstring != null) {
        // add class attributes described either in the init doc or class doc
        addAttributesSection(effectiveDocstring);

        if (effectiveDocstring == ownDocstring) {
          final PyFunction init = pyClass.findMethodByName(PyNames.INIT, false, myContext);
          if (init != null) {
            // add init parameters described in the class doc
            addFunctionSpecificSections(effectiveDocstring, init);
          }
        }
      }
    }
    else if (elementDefinition instanceof PyFunction pyFunction) {
      myBody.append(PythonDocumentationProvider.describeDecorators(pyFunction, HtmlChunk.br()));
      myBody.append(PythonDocumentationProvider.describeFunction(pyFunction, myContext, false));

      final PyClass pyClass = pyFunction.getContainingClass();
      if (!isProperty && pyClass != null) {
        final HtmlChunk link = getLinkToClass(pyClass, true);
        if (link != null) {
          myProlog.append(BOTTOM_ELEMENT
                            .children(
                              HtmlChunk.icon("AllIcons.Nodes.Class", AllIcons.Nodes.Class),
                              HtmlChunk.nbsp(),
                              link.code()
                            ));
        }
      }
      if (effectiveDocstring != null) {
        addFunctionSpecificSections(effectiveDocstring, pyFunction);
        // if function is init without doc we will take attributes from the class doc
        if (effectiveDocstring != ownDocstring && PyUtil.isInitOrNewMethod(pyFunction)) {
          addAttributesSection(effectiveDocstring);
        }
      }
    }
    else if (elementDefinition instanceof PyFile pyFile) {
      addModulePath(pyFile);
    }
    else if (elementDefinition instanceof PyTargetExpression target) {
      addTargetDocumentation(target, isProperty);
    }

    final String formatterInput = effectiveDocstring != null && !isProperty ? effectiveDocstring.getStringValue() : null;

    if (StringUtil.isEmpty(formatterInput) && myFormatterFragments.isEmpty()) return;

    final DocstringFormatterRequest output = PyStructuredDocstringFormatter.formatDocstring(myElement, new DocstringFormatterRequest(
      StringUtil.notNullize(formatterInput), myFormatterFragments), List.of(PyStructuredDocstringFormatter.FORMATTER_FRAGMENTS_FLAG));

    if (output != null) {
      myContent.appendRaw(output.getBody());
      fillFormattedSections(output.getFragments());
    }
    else {
      // if docstring turned out PLAIN, but suddenly we have fragments or body to handle
      if (effectiveDocstring != null) {
        myContent.append(updateLines(myElement, Strings.notNullize(formatterInput)));
      }
      fillFormattedSections(myFormatterFragments);
    }
  }

  private @Nullable PyStringLiteralExpression modifyDocStringByOwnerType(@Nullable PyStringLiteralExpression docstring,
                                                                         @NotNull PyDocStringOwner owner,
                                                                         boolean isProperty) {
    if (docstring != null) return docstring;

    if (owner instanceof PyClass pyClass) {
      final PyFunction init = pyClass.findMethodByName(PyNames.INIT, false, myContext);
      // if class doesn't have any doc return init doc
      if (init != null) {
        return getEffectiveDocStringExpression(init);
      }
    }
    else if (owner instanceof PyFunction pyFunction) {
      final PyClass containingClass = pyFunction.getContainingClass();
      if (containingClass != null && !isProperty) {
        // add docstring from the parent class or function
        return addFunctionInheritedDocString(pyFunction, containingClass);
      }
    }
    return docstring;
  }

  private void fillFormattedSections(@NotNull List<FormatterDocFragment> fragmentList) {
    fragmentList.forEach(fragment -> {
      final String html = fragment.html().toString();
      if (html.isEmpty()) return;
      final HtmlBuilder section = switch (fragment.myFragmentType) {
        case ATTRIBUTE -> mySectionsMap.get(PyPsiBundle.message("QDOC.attributes"));
        case PARAMETER -> mySectionsMap.get(PyPsiBundle.message("QDOC.params"));
        case KEYWORD_ARGUMENT -> mySectionsMap.get(PyPsiBundle.message("QDOC.keyword.args"));
        case RAISE -> mySectionsMap.get(PyPsiBundle.message("QDOC.raises"));
        case RETURN -> mySectionsMap.get(PyPsiBundle.message("QDOC.returns"));
      };
      section.appendRaw(html);
    });
  }

  private void addTargetDocumentation(@NotNull PyTargetExpression target, boolean isProperty) {
    if (isAttribute() && !isProperty) {
      final PyClass containingClass = target.getContainingClass();
      @SuppressWarnings("ConstantConditions") final HtmlChunk link = getLinkToClass(containingClass, true);
      String targetName = target.getName();
      if (link != null && targetName != null) {
        if (PyUtil.isInstanceAttribute(target)) {
          myBody.appendRaw(PyPsiBundle.message("QDOC.instance.attribute", HtmlChunk.text(targetName).bold(), link));
        }
        else {
          myBody.appendRaw(PyPsiBundle.message("QDOC.class.attribute", HtmlChunk.text(targetName).bold(), link));
        }
        myBody.br();
      }
      // if there is no separate doc for attribute we will try to take it from class doc
      if (getEffectiveDocStringExpression(target) == null) {
        final PyStringLiteralExpression docString = getEffectiveDocStringExpression(containingClass);
        if (docString != null) {
          final StructuredDocString structuredDocString = DocStringUtil.parse(docString.getStringValue());
          final String description = structuredDocString.getAttributeDescription(targetName);
          if (StringUtil.isNotEmpty(description)) {
            myContent.append(runFormatterService(description));
          }
        }
      }
    }
    myBody.append(PythonDocumentationProvider.describeTarget(target, myContext));
  }

  private void addAttributesSection(@NotNull PyStringLiteralExpression docstring) {
    final StructuredDocString structured = DocStringUtil.parseDocString(docstring);
    final List<String> documentedAttributes = structured.getAttributes();

    StreamEx.of(documentedAttributes).forEach(name -> {
      final String description = structured.getAttributeDescription(name);
      myFormatterFragments.add(new FormatterDocFragment(name, StringUtil.notNullize(description), FormatterDocFragmentType.ATTRIBUTE));
    });
  }

  private void addFunctionSpecificSections(@NotNull PyStringLiteralExpression docstring, @NotNull PyFunction function) {
    final StructuredDocString structured = DocStringUtil.parseDocString(docstring);

    final List<PyCallableParameter> parameters = function.getParameters(myContext);
    final List<String> actualNames = ContainerUtil.mapNotNull(parameters, PyCallableParameter::getName);
    StreamEx.of(actualNames).filter(name -> structured.getParamDescription(name) != null).forEach(name -> {
      final String description = structured.getParamDescription(name);
      myFormatterFragments.add(new FormatterDocFragment(name, StringUtil.notNullize(description), FormatterDocFragmentType.PARAMETER));
    });

    final List<String> allKeywordArgs = structured.getKeywordArguments();
    if (!ContainerUtil.exists(parameters, PyCallableParameter::isKeywordContainer)) {
      allKeywordArgs.retainAll(new HashSet<>(actualNames));
    }
    StreamEx.of(allKeywordArgs).forEach(name -> {
      final String description = structured.getKeywordArgumentDescription(name);
      myFormatterFragments.add(
        new FormatterDocFragment(name, StringUtil.notNullize(description), FormatterDocFragmentType.KEYWORD_ARGUMENT));
    });

    final String returnDescription = structured.getReturnDescription();
    if (returnDescription != null) {
      myFormatterFragments.add(new FormatterDocFragment("return", returnDescription, FormatterDocFragmentType.RETURN));
    }

    StreamEx.of(structured.getRaisedExceptions()).forEach(name -> {
      final String description = structured.getRaisedExceptionDescription(name);
      myFormatterFragments.add(new FormatterDocFragment(name, StringUtil.notNullize(description), FormatterDocFragmentType.RAISE));
    });
  }

  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  private @NotNull PsiElement resolveToDocStringOwner() {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression && ((PyTargetExpression)myElement).getDocStringValue() == null) {
      final PyExpression assignedValue = ((PyTargetExpression)myElement).findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression) {
        final PsiElement resolved = resolve((PyReferenceExpression)assignedValue);
        if (resolved instanceof PyDocStringOwner) {
          String name = ((PyTargetExpression)myElement).getName();
          if (name != null) {
            mySectionsMap.get(PyPsiBundle.message("QDOC.assigned.to")).append(HtmlChunk.text(name).code());
          }
          return resolved;
        }
      }
    }
    // Reference expression can be passed as the target element in Python console
    if (myElement instanceof PyReferenceExpression) {
      final PsiElement resolved = resolve((PyReferenceExpression)myElement);
      if (resolved != null) {
        return resolved;
      }
    }
    // Return wrapped function for functools.wraps decorated function
    if (myElement instanceof PyFunction function) {
      PyType type = new PyFunctoolsWrapsDecoratedFunctionTypeProvider().getCallableType(function, myContext);
      if (type instanceof PyCallableType callableType) {
        PyCallable callable = callableType.getCallable();
        if (callable != null) {
          return callable;
        }
      }
    }
    return myElement;
  }

  private @Nullable PsiElement resolve(@NotNull PyReferenceExpression element) {
    final PyResolveContext resolveContext = PyResolveContext.implicitContext(myContext);
    final QualifiedResolveResult resolveResult = element.followAssignmentsChain(resolveContext);
    return resolveResult.getElement();
  }

  private @Nullable PyStringLiteralExpression addFunctionInheritedDocString(@NotNull PyFunction pyFunction, @NotNull PyClass pyClass) {
    final String methodName = pyFunction.getName();
    if (methodName == null) {
      return null;
    }
    final boolean isConstructor = PyUtil.isInitOrNewMethod(pyFunction);
    List<PyClass> classes = pyClass.getAncestorClasses(myContext);
    if (isConstructor) {
      // look at our own class again and maybe inherit class's doc
      classes = ContainerUtil.prepend(classes, pyClass);
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
        final HtmlChunk ancestorLink = isFromClass ? getLinkToClass(ancestor, false) : getLinkToFunction(inherited, false);
        if (ancestorLink != null) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.copied.from")).append(ancestorLink.code());
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

  private @Nullable PyStringLiteralExpression addPredefinedMethodDoc(@NotNull PyFunction fun, @NotNull String methodName) {
    final PyClassType objectType = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objectType != null) {
      final PyClass objectClass = objectType.getPyClass();
      final PyFunction predefinedMethod = objectClass.findMethodByName(methodName, false, null);
      if (predefinedMethod != null) {
        final PyStringLiteralExpression predefinedDocstring = getEffectiveDocStringExpression(predefinedMethod);
        final String predefinedDoc = predefinedDocstring != null ? predefinedDocstring.getStringValue() : null;
        if (predefinedDoc != null && !predefinedDoc.isEmpty()) {
          mySectionsMap.get(PyPsiBundle.message("QDOC.copied.from")).append(PyPsiBundle.message("QDOC.built.in.description"));
          myContent.append(safeRunFormatterService(fun, predefinedDoc));
        }
        return predefinedDocstring;
      }
    }
    return null;
  }

  private static @NotNull HtmlChunk safeRunFormatterService(@NotNull PsiElement element, @NotNull String docstring) {
    final DocstringFormatterRequest formatted =
      PyStructuredDocstringFormatter.formatDocstring(element, new DocstringFormatterRequest(docstring), Collections.emptyList());
    if (formatted != null) {
      return HtmlChunk.raw(formatted.getBody());
    }
    return updateLines(element, docstring);
  }

  private static @NotNull HtmlChunk updateLines(@NotNull PsiElement element, @NotNull String docstring) {
    final List<String> origLines = LineTokenizer.tokenizeIntoList(docstring.trim(), false, false);
    final List<String> updatedLines = StreamEx.of(PyIndentUtil.removeCommonIndent(origLines, true))
      .takeWhile(line -> !line.startsWith(">>>")) //TODO: PyConsoleUtil.ORDINARY_PROMPT
      .toList();
    final HtmlBuilder result = new HtmlBuilder();
    // reconstruct back, dropping first empty fragment as needed
    boolean isFirstLine = true;
    final int tabSize = PythonCodeStyleService.getInstance().getTabSize(element.getContainingFile());
    for (@NlsSafe String line : updatedLines) {
      if (isFirstLine && ourSpacesPattern.matcher(line).matches()) continue; // ignore all initial whitespace
      if (isFirstLine) {
        isFirstLine = false;
      }
      else {
        result.br();
      }
      int leadingTabs = 0;
      while (leadingTabs < line.length() && line.charAt(leadingTabs) == '\t') {
        leadingTabs++;
      }
      if (leadingTabs > 0) {
        line = StringUtil.repeatSymbol(' ', tabSize * leadingTabs) + line.substring(leadingTabs);
      }
      result.append(HtmlChunk.text(line));
    }
    return result.toFragment();
  }

  private void addModulePath(@NotNull PyFile followed) {
    // what to prepend to a module description?
    final VirtualFile file = followed.getVirtualFile();
    if (file == null) {
      myBody.append(HtmlChunk.text(PyPsiBundle.message("QDOC.module.path.unknown")).wrapWith(HtmlChunk.tag("small")));
    }
    else {
      final QualifiedName name = QualifiedNameFinder.findShortestImportableQName(followed);
      if (name != null) {
        @NonNls String qualifiedName =
          ObjectUtils.chooseNotNull(QualifiedNameFinder.canonizeQualifiedName(followed, name, null), name).toString();
        if (PyUtil.isPackage(followed)) {
          myBody.appendRaw(PyPsiBundle.message("QDOC.package.name", HtmlChunk.text(qualifiedName).bold()));
        }
        else {
          myBody.appendRaw(PyPsiBundle.message("QDOC.module.name", HtmlChunk.text(qualifiedName).bold()));
        }
      }
      else {
        final @NonNls String path = file.getPath();
        myBody.append(HtmlChunk.raw(path).wrapWith(HtmlChunk.tag("span").attr("path", path)));
      }
    }
  }

  private static @Nullable HtmlChunk getLinkToModule(@NotNull PyFile module) {
    final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(module, null);
    if (name != null) {
      return PyDocumentationLink.toModule(name.toString(), name.toString());
    }
    final VirtualFile vFile = module.getVirtualFile();
    if (vFile != null) {
      @NlsSafe String vFilePath = vFile.getPath();
      return HtmlChunk.raw(vFilePath);
    }
    else {
      return null;
    }
  }

  private @Nullable HtmlChunk getLinkToClass(@NotNull PyClass pyClass, boolean preferQualifiedName) {
    final String qualifiedName = pyClass.getQualifiedName();
    final String shortName = pyClass.getName();

    final String linkText = preferQualifiedName && qualifiedName != null ? qualifiedName : shortName;
    if (linkText == null) {
      return null;
    }

    if (qualifiedName != null) {
      return PyDocumentationLink.toClass(pyClass, linkText);
    }
    else if (PsiTreeUtil.getParentOfType(myElement, PyClass.class, false) == pyClass) {
      return PyDocumentationLink.toContainingClass(linkText);
    }
    return HtmlChunk.raw(linkText);
  }

  private static @Nullable HtmlChunk getLinkToFunction(@NotNull PyFunction function, boolean preferQualifiedName) {
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
    return HtmlChunk.raw(linkText);
  }

  private static @Nullable HtmlChunk getLinkToTypeAliasStatement(@NotNull PyTypeAliasStatement typeAliasStatement) {
    final String linkText = typeAliasStatement.getQualifiedName();
    final PsiFile file = typeAliasStatement.getContainingFile();
    if (linkText == null || typeAliasStatement.getName() == null || file == null) {
      return null;
    }
    return PyDocumentationLink.toTypeAliasStatement(linkText, typeAliasStatement);
  }

  static @Nullable PyStringLiteralExpression getEffectiveDocStringExpression(@NotNull PyDocStringOwner owner) {
    final PyStringLiteralExpression expression = owner.getDocStringExpression();
    if (expression != null) {
      return expression;
    }
    final PsiElement original = PyiUtil.getOriginalElement(owner);
    final PyDocStringOwner originalOwner = as(original, PyDocStringOwner.class);
    return originalOwner != null ? originalOwner.getDocStringExpression() : null;
  }

  private enum FormatterDocFragmentType {
    ATTRIBUTE, PARAMETER, RETURN, RAISE, KEYWORD_ARGUMENT
  }

  @ApiStatus.Internal
  public static final class DocstringFormatterRequest {
    private final @NotNull @NlsSafe String body;
    private final @NotNull List<FormatterDocFragment> fragments;

    DocstringFormatterRequest() {
      body = "";
      fragments = Collections.emptyList();
    }

    @ApiStatus.Internal
    public DocstringFormatterRequest(@NotNull String body) {
      this.body = body;
      fragments = Collections.emptyList();
    }

    DocstringFormatterRequest(@NotNull String body, @NotNull List<FormatterDocFragment> fragments) {
      this.body = body;
      this.fragments = fragments;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DocstringFormatterRequest structure)) return false;
      return body.equals(structure.body) && fragments.equals(structure.fragments);
    }

    @Override
    public int hashCode() {
      return Objects.hash(body, fragments);
    }

    @NotNull @NlsSafe String getBody() {
      return body;
    }

    @NotNull List<FormatterDocFragment> getFragments() {
      return fragments;
    }
  }

  private static final class FormatterDocFragment {
    private static final @NlsSafe String NDASH = " &ndash; ";
    private final @NlsSafe String myName;
    private final @NlsSafe String myDescription;
    private final FormatterDocFragmentType myFragmentType;

    private FormatterDocFragment(@NotNull String name, @NotNull String description, @NotNull FormatterDocFragmentType type) {
      this.myName = name;
      this.myDescription = description;
      this.myFragmentType = type;
    }

    public @NotNull HtmlChunk html() {
      if (myName.isEmpty() && myFragmentType != FormatterDocFragmentType.RETURN) return HtmlChunk.empty();

      final HtmlBuilder builder = new HtmlBuilder();
      if (myFragmentType == FormatterDocFragmentType.RETURN) {
        return builder.appendRaw(myDescription).toFragment();
      }

      builder.append(HtmlChunk.raw(myName).code());
      if (!myDescription.isEmpty()) {
        builder.appendRaw(NDASH).appendRaw(myDescription);
      }
      return builder.wrapWith(HtmlChunk.p());
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) return true;
      if (obj == null || obj.getClass() != this.getClass()) return false;
      var that = (FormatterDocFragment)obj;
      return Objects.equals(myName, that.myName) &&
             Objects.equals(myDescription, that.myDescription) &&
             Objects.equals(myFragmentType, that.myFragmentType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myName, myDescription, myFragmentType);
    }
  }
}
