// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.documentation;

import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.io.HttpRequests;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDocumentationLinkProvider;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.PydevDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyClassImpl;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFile;
import com.jetbrains.python.pyi.PyiUtil;
import com.jetbrains.python.toolbox.ChainIterable;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;

/**
 * Provides quick docs for classes, methods, and functions.
 * Generates documentation stub
 */
public class PythonDocumentationProvider extends AbstractDocumentationProvider implements ExternalDocumentationProvider {

  // provides ctrl+hover info
  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, @NotNull PsiElement originalElement) {
    for (PythonDocumentationQuickInfoProvider point : PythonDocumentationQuickInfoProvider.EP_NAME.getExtensions()) {
      final String info = point.getQuickInfo(originalElement);
      if (info != null) {
        return info;
      }
    }

    final TypeEvalContext context = TypeEvalContext.userInitiated(originalElement.getProject(), originalElement.getContainingFile());

    if (element instanceof PyFunction) {
      final PyFunction function = (PyFunction)element;
      final ChainIterable<String> result = new ChainIterable<>();

      final PyClass cls = function.getContainingClass();
      if (cls != null) {
        final String clsName = cls.getName();
        if (clsName != null) {
          result.addItem("class ").addItem(clsName).addItem("\n");
          // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
        }
      }

      result
        .add(describeDecorators(function, Function.identity(), TO_ONE_LINE_AND_ESCAPE, ", ", "\n"))
        .add(describeFunction(function, Function.identity(), ESCAPE_ONLY, context));

      final String docStringSummary = getDocStringSummary(function);
      if (docStringSummary != null) {
        result.addItem("\n").addItem(docStringSummary);
      }

      return result.toString();
    }
    else if (element instanceof PyClass) {
      final PyClass cls = (PyClass)element;
      final ChainIterable<String> result = new ChainIterable<>();

      result
        .add(describeDecorators(cls, Function.identity(), TO_ONE_LINE_AND_ESCAPE, ", ", "\n"))
        .add(describeClass(cls, Function.identity(), TO_ONE_LINE_AND_ESCAPE, false, false, context));

      final String docStringSummary = getDocStringSummary(cls);
      if (docStringSummary != null) {
        result.addItem("\n").addItem(docStringSummary);
      }
      else {
        Optional
          .ofNullable(cls.findInitOrNew(false, context))
          .map(PythonDocumentationProvider::getDocStringSummary)
          .ifPresent(summary -> result.addItem("\n").addItem(summary));
      }

      return result.toString();
    }
    else if (element instanceof PyExpression) {
      return describeExpression((PyExpression)element, originalElement, ESCAPE_ONLY, context);
    }
    return null;
  }

  @Nullable
  private static String getDocStringSummary(@NotNull PyDocStringOwner owner) {
    final PyStringLiteralExpression docStringExpression = PyDocumentationBuilder.getEffectiveDocStringExpression(owner);
    if (docStringExpression != null) {
      final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue(), docStringExpression);
      return docString.getSummary();
    }
    return null;
  }

  @NotNull
  static ChainIterable<String> describeFunction(@NotNull PyFunction function,
                                                @NotNull Function<String, String> escapedNameMapper,
                                                @NotNull Function<String, String> escaper,
                                                @NotNull TypeEvalContext context) {

    final ChainIterable<String> result = describeFunctionWithTypes(function, escaper, escapedNameMapper, context);

    if (!PyiUtil.isOverload(function, context)) {
      final List<PyFunction> overloads = PyiUtil.getOverloads(function, context);
      if (!overloads.isEmpty()) {
        result.addItem(escaper.apply("\nPossible types:\n"));
        boolean first = true;
        for (PyFunction overload : overloads) {
          if (!first) {
            result.addItem(escaper.apply("\n"));
          }
          result.addItem(escaper.apply("\u2022 ")); // &bull; -- bullet point
          describeTypeWithLinks(context.getType(overload), context, function, result);
          first = false;
        }
      }
    }

    return result;

    //if (!PyNames.INIT.equals(name)) {
    //  result.addItem(escaper.apply("\nInferred type: "));
    //  describeTypeWithLinks(context.getType(function), context, function, result);
    //}
  }

  @NotNull
  static ChainIterable<String> describeTarget(@NotNull PyTargetExpression target, @NotNull TypeEvalContext context) {
    final ChainIterable<String> result = new ChainIterable<>();
    result.addItem(StringUtil.escapeXml(StringUtil.notNullize(target.getName())));
    result.addItem(": ");
    describeTypeWithLinks(context.getType(target), context, target, result);
    // Can return not physical elements such as foo()[0] for assignments like x, _ = foo()
    final PyExpression value = target.findAssignedValue();
    if (value != null) {
      result.addItem(" = ");
      final String initializerText = value.getText();
      final int index = initializerText.indexOf("\n");
      if (index < 0) {
        result.addItem(StringUtil.escapeXml(initializerText));
      }
      else {
        result.addItem(StringUtil.escapeXml(initializerText.substring(0, index))).addItem("...");
      }
    }
    return result;
  }

  @NotNull
  static ChainIterable<String> describeParameter(@NotNull PyNamedParameter parameter, @NotNull TypeEvalContext context) {
    final ChainIterable<String> result = new ChainIterable<>();
    result.addItem(StringUtil.escapeXml(StringUtil.notNullize(parameter.getName())));
    result.addItem(": ");
    describeTypeWithLinks(context.getType(parameter), context, parameter, result);
    return result;
  }

  @NotNull
  private static ChainIterable<String> describeFunctionWithTypes(@NotNull PyFunction function,
                                                                 @NotNull Function<String, String> escaper,
                                                                 @NotNull Function<String, String> escapedNameMapper,
                                                                 @NotNull TypeEvalContext context) {
    final ChainIterable<String> result = new ChainIterable<>();
    // TODO wrapping of long signatures
    result.addItem(ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES.apply("def ")).addItem(escapedNameMapper.apply(function.getName())).addItem("(");
    boolean first = true;
    for (PyCallableParameter parameter : function.getParameters(context)) {
      if (!first) {
        result.addItem(ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES.apply(", "));
      }
      result.addItem(parameter.getName()).addItem(ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES.apply(": "));
      describeTypeWithLinks(parameter.getType(context), context, function, result);
      first = false;
    }
    result.addItem(ESCAPE_AND_SAVE_NEW_LINES_AND_SPACES.apply(") -> "));
    describeTypeWithLinks(context.getReturnType(function), context, function, result);
    return result;
  }

  @Nullable
  private static String describeExpression(@NotNull PyExpression expression,
                                           @NotNull PsiElement originalElement,
                                           @NotNull Function<String, String> escaper,
                                           @NotNull TypeEvalContext context) {
    final String name = expression.getName();
    if (name != null) {
      final StringBuilder result = new StringBuilder(expression instanceof PyNamedParameter ? "parameter" : "variable");
      result.append(String.format(" \"%s\"", name));

      if (expression instanceof PyNamedParameter) {
        final PyFunction function = PsiTreeUtil.getParentOfType(expression, PyFunction.class);
        if (function != null) {
          result
            .append(" of ")
            .append(function.getContainingClass() == null ? "function" : "method")
            .append(String.format(" \"%s\"", function.getName()));
        }
      }

      if (originalElement instanceof PyTypedElement) {
        final String typeName = getTypeName(context.getType(((PyTypedElement)originalElement)), context);
        result
          .append("\n")
          .append(String.format("Inferred type: %s", typeName));
      }

      return escaper.apply(result.toString());
    }
    return null;
  }

  /**
   * @param type    type which name will be calculated
   * @param context type evaluation context
   * @return string representation of the type
   */
  @NotNull
  public static String getTypeName(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return buildTypeModel(type, context).asString();
  }

  /**
   * Returns the provided type in PEP 484 compliant format.
   */
  @NotNull
  public static String getTypeHint(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return buildTypeModel(type, context).asPep484TypeHint();
  }

  /**
   * @param type    type which description will be calculated.
   *                Description is the same as {@link PythonDocumentationProvider#getTypeDescription(PyType, TypeEvalContext)} gives but
   *                types are converted to links.
   * @param context type evaluation context
   * @param anchor  anchor element
   * @param body    body to be used to append description
   */
  public static void describeTypeWithLinks(@Nullable PyType type,
                                           @NotNull TypeEvalContext context,
                                           @NotNull PsiElement anchor,
                                           @NotNull ChainIterable<String> body) {
    buildTypeModel(type, context).toBodyWithLinks(body, anchor);
  }

  /**
   * @param type    type which description will be calculated
   * @param context type evaluation context
   * @return more user-friendly description than result of {@link PythonDocumentationProvider#getTypeName(PyType, TypeEvalContext)}.
   * {@code Any} is excluded from {@code Union[Any, ...]}-like types.
   */
  @NotNull
  public static String getTypeDescription(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return buildTypeModel(type, context).asDescription();
  }

  @NotNull
  private static PyTypeModelBuilder.TypeModel buildTypeModel(@Nullable PyType type, @NotNull TypeEvalContext context) {
    return new PyTypeModelBuilder(context).build(type, true);
  }

  @NotNull
  static ChainIterable<String> describeDecorators(@NotNull PyDecoratable decoratable,
                                                  @NotNull Function<String, String> escapedCalleeMapper,
                                                  @NotNull Function<String, String> escaper,
                                                  @NotNull String separator,
                                                  @NotNull String suffix) {
    final ChainIterable<String> result = new ChainIterable<>();

    final PyDecoratorList decoratorList = decoratable.getDecoratorList();
    if (decoratorList != null) {
      boolean first = true;

      for (PyDecorator decorator : decoratorList.getDecorators()) {
        if (!first) {
          result.addItem(separator);
        }
        result.add(describeDecorator(decorator, escapedCalleeMapper, escaper));
        first = false;
      }
    }

    if (!result.isEmpty()) {
      result.addItem(suffix);
    }

    return result;
  }

  @NotNull
  static ChainIterable<String> describeClass(@NotNull PyClass cls,
                                             @NotNull Function<String, String> escapedNameMapper,
                                             @NotNull Function<String, String> escaper,
                                             boolean link,
                                             boolean linkAncestors,
                                             @NotNull TypeEvalContext context) {
    final ChainIterable<String> result = new ChainIterable<>();

    final String name = escapedNameMapper.apply(escaper.apply(cls.getName()));
    result.addItem(escaper.apply("class "));
    result.addItem(link ? PyDocumentationLink.toContainingClass(name) : name);

    final PyExpression[] superClasses = cls.getSuperClassExpressions();
    if (superClasses.length > 0) {
      result.addItem(escaper.apply("("));
      boolean isNotFirst = false;

      for (PyExpression superClass : superClasses) {
        if (isNotFirst) {
          result.addItem(escaper.apply(", "));
        }
        else {
          isNotFirst = true;
        }

        result.addItem(describeSuperClass(superClass, escaper, linkAncestors, context));
      }

      result.addItem(escaper.apply(")"));
    }

    return result;
  }

  @NotNull
  private static String describeSuperClass(@NotNull PyExpression expression,
                                           @NotNull Function<String, String> escaper,
                                           boolean link,
                                           @NotNull TypeEvalContext context) {
    if (link) {
      if (expression instanceof PyReferenceExpression) {
        final PyReferenceExpression referenceExpression = (PyReferenceExpression)expression;
        if (!referenceExpression.isQualified()) {
          final PyResolveContext resolveContext = PyResolveContext.noImplicits().withTypeEvalContext(context);

          for (ResolveResult result : referenceExpression.getReference(resolveContext).multiResolve(false)) {
            final PsiElement element = result.getElement();
            if (element instanceof PyClass) {
              final String qualifiedName = ((PyClass)element).getQualifiedName();
              if (qualifiedName != null) {
                return PyDocumentationLink.toPossibleClass(escaper.apply(expression.getText()), qualifiedName, element, context);
              }
            }
          }
        }
      }
      else if (expression instanceof PySubscriptionExpression) {
        final PyExpression operand = ((PySubscriptionExpression)expression).getOperand();
        final PyExpression indexExpression = ((PySubscriptionExpression)expression).getIndexExpression();

        if (indexExpression != null) {
          return describeSuperClass(operand, escaper, true, context) +
                 escaper.apply("[") +
                 describeSuperClass(indexExpression, escaper, true, context) +
                 escaper.apply("]");
        }
      }
      else if (expression instanceof PyKeywordArgument) {
        final String keyword = ((PyKeywordArgument)expression).getKeyword();
        final PyExpression valueExpression = ((PyKeywordArgument)expression).getValueExpression();

        if (PyNames.METACLASS.equals(keyword) && valueExpression != null) {
          return escaper.apply(PyNames.METACLASS + "=") + describeSuperClass(valueExpression, escaper, true, context);
        }
      }
      else if (PyClassImpl.isSixWithMetaclassCall(expression)) {
        final PyCallExpression callExpression = (PyCallExpression)expression;
        final PyExpression callee = callExpression.getCallee();

        if (callee != null) {
          return StreamEx
            .of(callExpression.getArguments())
            .map(argument -> describeSuperClass(argument, escaper, true, context))
            .joining(escaper.apply(", "), escaper.apply(callee.getText() + "("), escaper.apply(")"));
        }
      }
    }

    return escaper.apply(expression.getText());
  }

  @NotNull
  private static Iterable<String> describeDecorator(@NotNull PyDecorator decorator,
                                                    @NotNull Function<String, String> escapedCalleeMapper,
                                                    @NotNull Function<String, String> escaper) {
    final ChainIterable<String> result = new ChainIterable<>();

    result
      .addItem(escaper.apply("@"))
      .addItem(escapedCalleeMapper.apply(escaper.apply(PyUtil.getReadableRepr(decorator.getCallee(), false))));

    final PyArgumentList argumentList = decorator.getArgumentList();
    if (argumentList != null) {
      result.addItem(escaper.apply(PyUtil.getReadableRepr(argumentList, false)));
    }

    return result;
  }

  // provides ctrl+Q doc
  @Override
  public String generateDoc(@NotNull PsiElement element, @Nullable PsiElement originalElement) {
    if (PydevConsoleRunner.isInPydevConsole(element) || originalElement != null && PydevConsoleRunner.isInPydevConsole(originalElement)) {
      return PydevDocumentationProvider.createDoc(element, originalElement);
    }
    return new PyDocumentationBuilder(element, originalElement).build();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, @NotNull String link, @NotNull PsiElement context) {
    return PyDocumentationLink.elementForLink(link,
                                              context,
                                              TypeEvalContext.userInitiated(context.getProject(), context.getContainingFile()));
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    final String url = getUrlFor(element, originalElement, true);
    return url == null ? null : Collections.singletonList(url);
  }

  @Nullable
  public static String getUrlFor(PsiElement element, PsiElement originalElement, boolean checkExistence) {
    PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
    if (file == null) return null;
    if (PyNames.INIT_DOT_PY.equals(file.getName())) {
      file = file.getParent();
      assert file != null;
    }
    final Sdk sdk = PyBuiltinCache.findSdkForFile(file);
    if (sdk == null) {
      return null;
    }
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, originalElement);
    if (qName == null) {
      return null;
    }
    final PythonDocumentationMap map = PythonDocumentationMap.getInstance();
    final String pyVersion = pyVersion(sdk.getVersionString());
    PsiNamedElement namedElement = (element instanceof PsiNamedElement && !(element instanceof PsiFileSystemItem))
                                   ? (PsiNamedElement)element
                                   : null;
    if (namedElement instanceof PyFunction && PyNames.INIT.equals(namedElement.getName())) {
      final PyClass containingClass = ((PyFunction)namedElement).getContainingClass();
      if (containingClass != null) {
        namedElement = containingClass;
      }
    }
    final String url = map.urlFor(qName, namedElement, pyVersion);
    if (url != null) {
      if (checkExistence && !pageExists(url)) {
        return map.rootUrlFor(qName);
      }
      return url;
    }
    for (PythonDocumentationLinkProvider provider : Extensions.getExtensions(PythonDocumentationLinkProvider.EP_NAME)) {
      final String providerUrl = provider.getExternalDocumentationUrl(element, originalElement);
      if (providerUrl != null) {
        if (checkExistence && !pageExists(providerUrl)) {
          return provider.getExternalDocumentationRoot(sdk);
        }
        return providerUrl;
      }
    }
    return null;
  }

  private static boolean pageExists(@NotNull String url) {
    if (new File(url).exists()) {
      return true;
    }
    try {
      HttpRequests.head(url).tryConnect();
    }
    catch (HttpRequests.HttpStatusException e) {
      return false;
    }
    catch (IllegalArgumentException e) {
      return false;
    }
    catch (IOException ignored) {
    }
    return true;
  }

  @Nullable
  public static String pyVersion(@Nullable String versionString) {
    final String prefix = "Python ";
    if (versionString != null && versionString.startsWith(prefix)) {
      final String version = versionString.substring(prefix.length());
      int dot = version.indexOf('.');
      if (dot > 0) {
        dot = version.indexOf('.', dot + 1);
        if (dot > 0) {
          return version.substring(0, dot);
        }
        return version;
      }
    }
    return null;
  }

  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
      final Module module = ModuleUtilCore.findModuleForPsiElement(element);
      if (module != null && !PyDocumentationSettings.getInstance(module).isRenderExternalDocumentation()) return null;
      PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
      if (file instanceof PyiFile) {
        return null;
      }
      if (file == null) return null;
      if (PyNames.INIT_DOT_PY.equals(file.getName())) {
        file = file.getParent();
        assert file != null;
      }
      final Sdk sdk = PyBuiltinCache.findSdkForFile(file);
      if (sdk == null) {
        return null;
      }

      final QualifiedName moduleQName = QualifiedNameFinder.findCanonicalImportPath(element, element);
      if (moduleQName == null) {
        return null;
      }
      PsiNamedElement namedElement = (element instanceof PsiNamedElement && !(element instanceof PsiFileSystemItem))
                                     ? (PsiNamedElement)element
                                     : null;
      if (namedElement instanceof PyFunction && PyNames.INIT.equals(namedElement.getName())) {
        final PyClass containingClass = ((PyFunction)namedElement).getContainingClass();
        if (containingClass != null) {
          namedElement = containingClass;
        }
      }
      final PyStdlibDocumentationLinkProvider stdlibDocumentationLinkProvider =
        Extensions.findExtension(PythonDocumentationLinkProvider.EP_NAME, PyStdlibDocumentationLinkProvider.class);
      final String url = stdlibDocumentationLinkProvider.getExternalDocumentationUrl(element, element);
      if (url == null) {
        return null;
      }

      try {
        final Document document = Jsoup.parse(new URL(url), 1000);
        final String elementId = namedElement != null ? moduleQName + "." + namedElement.getName() : "module-" + moduleQName;
        document.select("a.headerlink").remove();
        final Elements parents = document.getElementsByAttributeValue("id", elementId).parents();
        if (parents.isEmpty()) {
          final Elements moduleElement = document.getElementsByAttributeValue("id", "module-" + moduleQName.toString());
          if (moduleElement != null) {
            return moduleElement.toString();
          }
          return document.toString();
        }
        return parents.get(0).toString();
      }
      catch (MalformedURLException ignored) {
      }
      catch (IOException ignored) {
      }
      return null;
    });
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return getUrlFor(element, originalElement, false) != null;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(@NotNull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      final Project project = element.getProject();
      final VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile != null && ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
        final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
        if (qName != null && qName.getComponentCount() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(@NotNull PsiElement element) {
    final Project project = element.getProject();
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
    if (qName != null && qName.getComponentCount() > 0) {
      ApplicationManager.getApplication().invokeLater(() -> {
        final int rc = Messages.showOkCancelDialog(project,
                                                   "No external documentation URL configured for module " + qName.getComponents().get(0) +
                                                   ".\nWould you like to configure it now?",
                                                   "Python External Documentation",
                                                   Messages.getQuestionIcon());
        if (rc == Messages.OK) {
          ShowSettingsUtilImpl.showSettingsDialog(project, PythonDocumentationConfigurable.ID, "");
        }
      }, ModalityState.NON_MODAL);
    }
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@NotNull Editor editor,
                                                  @NotNull PsiFile file,
                                                  @Nullable PsiElement contextElement) {
    if (contextElement != null) {
      final IElementType elementType = contextElement.getNode().getElementType();
      if (PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(elementType)) {
        return contextElement;
      }
      final PsiElement parent = contextElement.getParent();
      if (parent instanceof PyArgumentList && (PyTokenTypes.LPAR == elementType || PyTokenTypes.RPAR == elementType)) {
        final PyCallExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyCallExpression.class);
        if (expression != null) {
          final PyExpression callee = expression.getCallee();
          if (callee != null) {
            final PsiReference reference = callee.getReference();
            if (reference != null) {
              return reference.resolve();
            }
          }
        }
      }
      final PyExpression expression = PsiTreeUtil.getParentOfType(contextElement, PyExpression.class);
      if (expression != null && DocStringUtil.isDocStringExpression(expression)) {
        final PyDocStringOwner docstringOwner = PsiTreeUtil.getParentOfType(contextElement, PyDocStringOwner.class);
        if (docstringOwner != null) return docstringOwner;
      }
    }
    return super.getCustomDocumentationElement(editor, file, contextElement);
  }
}
