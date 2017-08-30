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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibDocumentationLinkProvider;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.PydevDocumentationProvider;
import com.jetbrains.python.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParser;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.FP;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.jetbrains.annotations.NonNls;
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

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;

/**
 * Provides quick docs for classes, methods, and functions.
 * Generates documentation stub
 */
public class PythonDocumentationProvider extends AbstractDocumentationProvider implements ExternalDocumentationProvider {

  @NonNls static final String LINK_TYPE_CLASS = "#class#";
  @NonNls static final String LINK_TYPE_PARENT = "#parent#";
  @NonNls static final String LINK_TYPE_PARAM = "#param#";
  @NonNls static final String LINK_TYPE_TYPENAME = "#typename#";

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

    if (element instanceof PyFunction) {
      final PyFunction func = (PyFunction)element;
      final StringBuilder cat = new StringBuilder();
      final PyClass cls = func.getContainingClass();
      if (cls != null) {
        final String clsName = cls.getName();
        cat.append("class ").append(clsName).append("\n");
        // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
      }
      String summary = "";
      final PyStringLiteralExpression docStringExpression = PyDocumentationBuilder.getEffectiveDocStringExpression(func);
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue(), docStringExpression);
        summary = docString.getSummary();
      }
      return $(cat.toString()).add(describeDecorators(func, LSame2, ", ", LSame1)).add(describeFunction(func, LSame2, LSame1))
               .toString() + "\n" + summary;
    }
    else if (element instanceof PyClass) {
      final PyClass cls = (PyClass)element;
      String summary = "";
      PyStringLiteralExpression docStringExpression = PyDocumentationBuilder.getEffectiveDocStringExpression(cls);
      if (docStringExpression == null) {
        final PyFunction initOrNew = cls.findInitOrNew(false, null);
        if (initOrNew != null) {
          docStringExpression = PyDocumentationBuilder.getEffectiveDocStringExpression(initOrNew);
        }
      }
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue(), docStringExpression);
        summary = docString.getSummary();
      }

      return describeDecorators(cls, LSame2, ", ", LSame1).add(describeClass(cls, LSame2, false, false)).toString() + "\n" + summary;
    }
    else if (element instanceof PyExpression) {
      return describeExpression((PyExpression)element, originalElement);
    }
    return null;
  }

  /**
   * Creates a HTML description of function definition.
   *
   * @param fun             the function
   * @param funcNameWrapper puts a tag around the function name
   * @param escaper         sanitizes values that come directly from doc string or code
   * @return chain of strings for further chaining
   */
  @NotNull
  static ChainIterable<String> describeFunction(@NotNull PyFunction fun,
                                                FP.Lambda1<Iterable<String>, Iterable<String>> funcNameWrapper,
                                                @NotNull FP.Lambda1<String, String> escaper
  ) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final String name = fun.getName();
    cat.addItem("def ").addWith(funcNameWrapper, $(name));
    cat.addItem(escaper.apply(PyUtil.getReadableRepr(fun.getParameterList(), false)));
    if (!PyNames.INIT.equals(name)) {
      cat.addItem(escaper.apply("\nInferred type: "));
      describeTypeWithLinks(fun, cat);
      cat.addItem(BR);
    }
    return cat;
  }

  @Nullable
  private static String describeExpression(@NotNull PyExpression expr, @NotNull PsiElement originalElement) {
    final String name = expr.getName();
    if (name != null) {
      final StringBuilder result = new StringBuilder((expr instanceof PyNamedParameter) ? "parameter" : "variable");
      result.append(String.format(" \"%s\"", name));
      if (expr instanceof PyNamedParameter) {
        final PyFunction function = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
        if (function != null) {
          result.append(" of ").append(function.getContainingClass() == null ? "function" : "method");
          result.append(String.format(" \"%s\"", function.getName()));
        }
      }
      if (originalElement instanceof PyTypedElement) {
        final String typeName = getTypeName(((PyTypedElement)originalElement));
        result
          .append("\n")
          .append(String.format("Inferred type: %s", typeName));
      }
      return result.toString();
    }
    return null;
  }

  @NotNull
  private static String getTypeName(@NotNull PyTypedElement element) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    return getTypeName(context.getType(element), context);
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

  private static void describeTypeWithLinks(@NotNull PyTypedElement element, @NotNull ChainIterable<String> body) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    describeTypeWithLinks(context.getType(element), context, element, body);
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
  static ChainIterable<String> describeDecorators(@NotNull PyDecoratable what,
                                                  FP.Lambda1<Iterable<String>, Iterable<String>> decoNameWrapper,
                                                  @NotNull String decoSeparator,
                                                  FP.Lambda1<String, String> escaper) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final PyDecoratorList decoList = what.getDecoratorList();
    if (decoList != null) {
      for (PyDecorator deco : decoList.getDecorators()) {
        cat.add(describeDeco(deco, decoNameWrapper, escaper)).addItem(decoSeparator); // can't easily pass describeDeco to map() %)
      }
    }
    return cat;
  }

  /**
   * Creates a HTML description of function definition.
   *
   * @param cls         the class
   * @param nameWrapper wrapper to render the name with
   * @param allowHtml
   * @param linkOwnName if true, add link to class's own name  @return cat for easy chaining
   */
  @NotNull
  static ChainIterable<String> describeClass(@NotNull PyClass cls,
                                             FP.Lambda1<Iterable<String>, Iterable<String>> nameWrapper,
                                             boolean allowHtml,
                                             boolean linkOwnName) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final String name = cls.getName();
    cat.addItem("class ");
    if (allowHtml && linkOwnName) {
      cat.addWith(LinkMyClass, $(name));
    }
    else {
      cat.addWith(nameWrapper, $(name));
    }
    final PyExpression[] ancestors = cls.getSuperClassExpressions();
    if (ancestors.length > 0) {
      cat.addItem("(");
      boolean isNotFirst = false;
      for (PyExpression parent : ancestors) {
        final String parentName = parent.getName();
        if (parentName == null) {
          continue;
        }
        if (isNotFirst) {
          cat.addItem(", ");
        }
        else {
          isNotFirst = true;
        }
        if (allowHtml) {
          cat.addWith(new LinkWrapper(LINK_TYPE_PARENT + parentName), $(parentName));
        }
        else {
          cat.addItem(parentName);
        }
      }
      cat.addItem(")");
    }
    return cat;
  }

  //
  @NotNull
  private static Iterable<String> describeDeco(@NotNull PyDecorator deco,
                                               FP.Lambda1<Iterable<String>, Iterable<String>> nameWrapper,
                                               //  addWith in tags, if need be
                                               FP.Lambda1<String, String> argWrapper
                                               // add escaping, if need be
  ) {
    final ChainIterable<String> cat = new ChainIterable<>();
    cat.addItem("@").addWith(nameWrapper, $(PyUtil.getReadableRepr(deco.getCallee(), true)));
    if (deco.hasArgumentList()) {
      final PyArgumentList arglist = deco.getArgumentList();
      if (arglist != null) {
        cat
          .addItem("(")
          .add(interleave(FP.map(FP.combine(LReadableRepr, argWrapper), arglist.getArguments()), ", "))
          .addItem(")")
        ;
      }
    }
    return cat;
  }

  // provides ctrl+Q doc
  @Override
  public String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && PydevConsoleRunner.isInPydevConsole(element) ||
        originalElement != null && PydevConsoleRunner.isInPydevConsole(originalElement)) {
      return PydevDocumentationProvider.createDoc(element, originalElement);
    }
    return new PyDocumentationBuilder(element, originalElement).build();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, @NotNull String link, @NotNull PsiElement context) {
    if (link.equals(LINK_TYPE_CLASS)) {
      return inferContainingClassOf(context);
    }
    else if (link.equals(LINK_TYPE_PARAM)) {
      return inferClassOfParameter(context);
    }
    else if (link.startsWith(LINK_TYPE_PARENT)) {
      final PyClass cls = inferContainingClassOf(context);
      if (cls != null) {
        final String desiredName = link.substring(LINK_TYPE_PARENT.length());
        for (PyClass parent : cls.getAncestorClasses(null)) {
          final String parentName = parent.getName();
          if (parentName != null && parentName.equals(desiredName)) return parent;
        }
      }
    }
    else if (link.startsWith(LINK_TYPE_TYPENAME)) {
      final String typeName = link.substring(LINK_TYPE_TYPENAME.length());
      final PyType type = PyTypeParser.getTypeByName(context, typeName);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
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
    final HttpClient client = new HttpClient();
    final HttpConnectionManagerParams params = client.getHttpConnectionManager().getParams();
    params.setSoTimeout(5 * 1000);
    params.setConnectionTimeout(5 * 1000);

    try {
      final HeadMethod method = new HeadMethod(url);
      final int rc = client.executeMethod(method);
      if (rc == 404) {
        return false;
      }
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
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module != null && !PyDocumentationSettings.getInstance(module).isRenderExternalDocumentation()) return null;
    return ApplicationManager.getApplication().runReadAction((Computable<String>)() -> {
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
    if (contextElement != null &&
        PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(contextElement.getNode().getElementType())) {
      return contextElement;
    }
    return super.getCustomDocumentationElement(editor, file, contextElement);
  }

  @Nullable
  private static PyClass inferContainingClassOf(PsiElement context) {
    if (context instanceof PyClass) return (PyClass)context;
    if (context instanceof PyFunction) {
      return ((PyFunction)context).getContainingClass();
    }
    else {
      return PsiTreeUtil.getParentOfType(context, PyClass.class);
    }
  }

  @Nullable
  private static PyClass inferClassOfParameter(@NotNull PsiElement context) {
    if (context instanceof PyNamedParameter) {
      final PyType type = TypeEvalContext.userInitiated(context.getProject(), context.getContainingFile()).getType(
        (PyNamedParameter)context);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
  }

  public static final LinkWrapper LinkMyClass = new LinkWrapper(LINK_TYPE_CLASS);
  // link item to containing class
}
