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

import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.lang.documentation.AbstractDocumentationProvider;
import com.intellij.lang.documentation.ExternalDocumentationProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.console.PydevConsoleRunner;
import com.jetbrains.python.console.PydevDocumentationProvider;
import com.jetbrains.python.debugger.PySignature;
import com.jetbrains.python.debugger.PySignatureCacheManager;
import com.jetbrains.python.debugger.PySignatureUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.intellij.psi.util.QualifiedName;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.FP;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
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

  @NonNls private static final String RST_PREFIX = ":";
  @NonNls private static final String EPYDOC_PREFIX = "@";

  // provides ctrl+hover info
  public String getQuickNavigateInfo(final PsiElement element, PsiElement originalElement) {
    if (element instanceof PyFunction) {
      PyFunction func = (PyFunction)element;
      StringBuilder cat = new StringBuilder();
      PyClass cls = func.getContainingClass();
      if (cls != null) {
        String cls_name = cls.getName();
        cat.append("class ").append(cls_name).append("\n");
        // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
      }
      String summary = "";
      final PyStringLiteralExpression docStringExpression = func.getDocStringExpression();
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue());
        if (docString != null) {
          summary = docString.getSummary();
        }
      }
      return $(cat.toString()).add(describeDecorators(func, LSame2, ", ", LSame1)).add(describeFunction(func, LSame2, LSame1))
               .toString() + "\n" + summary;
    }
    else if (element instanceof PyClass) {
      PyClass cls = (PyClass)element;
      String summary = "";
      PyStringLiteralExpression docStringExpression = cls.getDocStringExpression();
      if (docStringExpression == null) {
        final PyFunction initOrNew = cls.findInitOrNew(false);
        if (initOrNew != null) {
          docStringExpression = initOrNew.getDocStringExpression();
        }
      }
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue());
        if (docString != null) {
          summary = docString.getSummary();
        }
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
   * @param fun               the function
   * @param func_name_wrapper puts a tag around the function name
   * @param escaper           sanitizes values that come directly from doc string or code
   * @return chain of strings for further chaining
   */
  static ChainIterable<String> describeFunction(
    PyFunction fun,
    FP.Lambda1<Iterable<String>, Iterable<String>> func_name_wrapper,
    FP.Lambda1<String, String> escaper
  ) {
    ChainIterable<String> cat = new ChainIterable<String>();
    final String name = fun.getName();
    cat.addItem("def ").addWith(func_name_wrapper, $(name));
    final TypeEvalContext context = TypeEvalContext.userInitiated(fun.getContainingFile());
    final List<PyParameter> parameters = PyUtil.getParameters(fun, context);
    final String paramStr = "(" +
                            StringUtil.join(parameters,
                                            new Function<PyParameter, String>() {
                                              @Override
                                              public String fun(PyParameter parameter) {
                                                return PyUtil.getReadableRepr(parameter, false);
                                              }
                                            },
                                            ", ") +
                            ")";
    cat.addItem(escaper.apply(paramStr));
    if (!PyNames.INIT.equals(name)) {
      cat.addItem(escaper.apply("\nInferred type: "));
      getTypeDescription(fun, cat);
      cat.addItem(BR);
    }
    return cat;
  }

  @Nullable
  private static String describeExpression(@NotNull PyExpression expr, @NotNull PsiElement originalElement) {
    final String name = expr.getName();
    if (name != null) {
      StringBuilder result = new StringBuilder((expr instanceof PyNamedParameter) ? "parameter" : "variable");
      result.append(String.format(" \"%s\"", name));
      if (expr instanceof PyNamedParameter) {
        final PyFunction function = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
        if (function != null) {
          result.append(" of ").append(function.getContainingClass() == null ? "function" : "method");
          result.append(String.format(" \"%s\"", function.getName()));
        }
      }
      if (originalElement instanceof PyTypedElement) {
        result.append("\n").append(describeType((PyTypedElement)originalElement));
      }
      return result.toString();
    }
    return null;
  }

  static String describeType(@NotNull PyTypedElement element) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getContainingFile());
    return String.format("Inferred type: %s", getTypeName(context.getType(element), context));
  }

  public static void getTypeDescription(@NotNull PyFunction fun, ChainIterable<String> body) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(fun.getContainingFile());
    PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    builder.build(context.getType(fun), true).toBodyWithLinks(body, fun);
  }

  public static String getTypeName(@Nullable PyType type, @NotNull final TypeEvalContext context) {
    PyTypeModelBuilder.TypeModel typeModel = buildTypeModel(type, context);
    return typeModel.asString();
  }

  private static PyTypeModelBuilder.TypeModel buildTypeModel(PyType type, TypeEvalContext context) {
    PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    return builder.build(type, true);
  }

  public static void describeExpressionTypeWithLinks(ChainIterable<String> body,
                                                     PyReferenceExpression expression,
                                                     @NotNull TypeEvalContext context) {
    PyType type = context.getType(expression);
    describeTypeWithLinks(body, expression, type, context);
  }

  public static void describeTypeWithLinks(ChainIterable<String> body,
                                           PsiElement anchor,
                                           PyType type, TypeEvalContext context) {
    PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    builder.build(type, true).toBodyWithLinks(body, anchor);
  }


  static ChainIterable<String> describeDecorators(PyDecoratable what, FP.Lambda1<Iterable<String>, Iterable<String>> deco_name_wrapper,
                                                  String deco_separator, FP.Lambda1<String, String> escaper) {
    ChainIterable<String> cat = new ChainIterable<String>();
    PyDecoratorList deco_list = what.getDecoratorList();
    if (deco_list != null) {
      for (PyDecorator deco : deco_list.getDecorators()) {
        cat.add(describeDeco(deco, deco_name_wrapper, escaper)).addItem(deco_separator); // can't easily pass describeDeco to map() %)
      }
    }
    return cat;
  }

  /**
   * Creates a HTML description of function definition.
   *
   * @param cls           the class
   * @param name_wrapper  wrapper to render the name with
   * @param allow_html
   * @param link_own_name if true, add link to class's own name  @return cat for easy chaining
   */
  static ChainIterable<String> describeClass(PyClass cls,
                                             FP.Lambda1<Iterable<String>, Iterable<String>> name_wrapper,
                                             boolean allow_html,
                                             boolean link_own_name) {
    ChainIterable<String> cat = new ChainIterable<String>();
    final String name = cls.getName();
    cat.addItem("class ");
    if (allow_html && link_own_name) {
      cat.addWith(LinkMyClass, $(name));
    }
    else {
      cat.addWith(name_wrapper, $(name));
    }
    final PyExpression[] ancestors = cls.getSuperClassExpressions();
    if (ancestors.length > 0) {
      cat.addItem("(");
      boolean is_not_first = false;
      for (PyExpression parent : ancestors) {
        final String parentName = parent.getName();
        if (parentName == null) {
          continue;
        }
        if (is_not_first) {
          cat.addItem(", ");
        }
        else {
          is_not_first = true;
        }
        if (allow_html) {
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
  private static Iterable<String> describeDeco(PyDecorator deco,
                                               final FP.Lambda1<Iterable<String>, Iterable<String>> name_wrapper,
                                               //  addWith in tags, if need be
                                               final FP.Lambda1<String, String> arg_wrapper
                                               // add escaping, if need be
  ) {
    ChainIterable<String> cat = new ChainIterable<String>();
    cat.addItem("@").addWith(name_wrapper, $(PyUtil.getReadableRepr(deco.getCallee(), true)));
    if (deco.hasArgumentList()) {
      PyArgumentList arglist = deco.getArgumentList();
      if (arglist != null) {
        cat
          .addItem("(")
          .add(interleave(FP.map(FP.combine(LReadableRepr, arg_wrapper), arglist.getArguments()), ", "))
          .addItem(")")
        ;
      }
    }
    return cat;
  }

  // provides ctrl+Q doc
  public String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && PydevConsoleRunner.isInPydevConsole(element) ||
        originalElement != null && PydevConsoleRunner.isInPydevConsole(originalElement)) {
      return PydevDocumentationProvider.createDoc(element, originalElement);
    }

    originalElement = findRealOriginalElement(originalElement); //original element can be whitespace or bracket,
    // but we need identifier that resolves to element

    return new PyDocumentationBuilder(element, originalElement).build();
  }

  private static PsiElement findRealOriginalElement(@Nullable PsiElement element) {
    if (element == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return element;
    }
    Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(file);
    if (document == null) {
      return element;
    }
    int newOffset = TargetElementUtilBase.adjustOffset(file, document, element.getTextOffset());
    PsiElement newElement = file.findElementAt(newOffset);
    return newElement != null ? newElement : element;
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, String link, PsiElement context) {
    if (link.equals(LINK_TYPE_CLASS)) {
      return inferContainingClassOf(context);
    }
    else if (link.equals(LINK_TYPE_PARAM)) {
      return inferClassOfParameter(context);
    }
    else if (link.startsWith(LINK_TYPE_PARENT)) {
      PyClass cls = inferContainingClassOf(context);
      if (cls != null) {
        String desired_name = link.substring(LINK_TYPE_PARENT.length());
        for (PyClass parent : cls.getAncestorClasses()) {
          final String parent_name = parent.getName();
          if (parent_name != null && parent_name.equals(desired_name)) return parent;
        }
      }
    }
    else if (link.startsWith(LINK_TYPE_TYPENAME)) {
      String typeName = link.substring(LINK_TYPE_TYPENAME.length());
      PyType type = PyTypeParser.getTypeByName(context, typeName);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(final PsiElement element, PsiElement originalElement) {
    final String url = getUrlFor(element, originalElement, true);
    return url == null ? null : Collections.singletonList(url);
  }

  @Nullable
  private static String getUrlFor(PsiElement element, PsiElement originalElement, boolean checkExistence) {
    PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
    if (file == null) return null;
    if (PyNames.INIT_DOT_PY.equals(file.getName())) {
      file = file.getParent();
      assert file != null;
    }
    Sdk sdk = PyBuiltinCache.findSdkForFile(file);
    if (sdk == null) {
      return null;
    }
    QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, originalElement);
    if (qName == null) {
      return null;
    }
    PythonDocumentationMap map = PythonDocumentationMap.getInstance();
    String pyVersion = pyVersion(sdk.getVersionString());
    PsiNamedElement namedElement = (element instanceof PsiNamedElement && !(element instanceof PsiFileSystemItem))
                                   ? (PsiNamedElement)element
                                   : null;
    if (namedElement instanceof PyFunction && PyNames.INIT.equals(namedElement.getName())) {
      final PyClass containingClass = ((PyFunction)namedElement).getContainingClass();
      if (containingClass != null) {
        namedElement = containingClass;
      }
    }
    String url = map.urlFor(qName, namedElement, pyVersion);
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

  private static boolean pageExists(String url) {
    if (new File(url).exists()) {
      return true;
    }
    HttpClient client = new HttpClient();
    client.setTimeout(5 * 1000);
    client.setConnectionTimeout(5 * 1000);
    try {
      HeadMethod method = new HeadMethod(url);
      int rc = client.executeMethod(method);
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
    String prefix = "Python ";
    if (versionString != null && versionString.startsWith(prefix)) {
      String version = versionString.substring(prefix.length());
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
    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return getUrlFor(element, originalElement, false) != null;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(PsiElement element) {
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
  public void promptToConfigureDocumentation(PsiElement element) {
    final Project project = element.getProject();
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
    if (qName != null && qName.getComponentCount() > 0) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          int rc = Messages.showOkCancelDialog(project,
                                               "No external documentation URL configured for module " + qName.getComponents().get(0) +
                                               ".\nWould you like to configure it now?",
                                               "Python External Documentation",
                                               Messages.getQuestionIcon());
          if (rc == 0) {
            ShowSettingsUtilImpl.showSettingsDialog(project, PythonDocumentationConfigurable.ID, "");
          }
        }
      }, ModalityState.NON_MODAL);
    }
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
  private static PyClass inferClassOfParameter(PsiElement context) {
    if (context instanceof PyNamedParameter) {
      final PyType type = TypeEvalContext.userInitiated(context.getContainingFile()).getType((PyNamedParameter)context);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
  }

  public static final LinkWrapper LinkMyClass = new LinkWrapper(LINK_TYPE_CLASS);
  // link item to containing class

  public static String generateDocumentationContentStub(PyFunction element, String offset, boolean checkReturn) {
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) return "";
    PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    String result = "";
    if (documentationSettings.isEpydocFormat(element.getContainingFile())) {
      result += generateContent(element, offset, EPYDOC_PREFIX, checkReturn);
    }
    else if (documentationSettings.isReSTFormat(element.getContainingFile())) {
      result += generateContent(element, offset, RST_PREFIX, checkReturn);
    }
    else {
      result += offset;
    }
    return result;
  }

  public static void insertDocStub(PyFunction function, PyStatementList insertPlace, Project project, Editor editor) {
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(insertPlace, PsiWhiteSpace.class);
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 1) {
        ws += spaces[spaces.length - 1];
      }
    }
    String docContent = ws + generateDocumentationContentStub(function, ws, true);
    PyExpressionStatement string = elementGenerator.createDocstring("\"\"\"" + docContent + "\"\"\"");
    if (insertPlace != null) {
      final PyStatement[] statements = insertPlace.getStatements();
      if (statements.length != 0) {
        insertPlace.addBefore(string, statements[0]);
      }
    }
    PyStringLiteralExpression docstring = function.getDocStringExpression();
    if (editor != null && docstring != null) {
      int offset = docstring.getTextOffset();
      editor.getCaretModel().moveToOffset(offset);
      editor.getCaretModel().moveCaretRelatively(0, 1, false, false, false);
    }
  }

  public String generateDocumentationContentStub(PyFunction element, boolean checkReturn) {
    PsiWhiteSpace whitespace = PsiTreeUtil.getPrevSiblingOfType(element.getStatementList(), PsiWhiteSpace.class);
    String ws = "\n";
    if (whitespace != null) {
      String[] spaces = whitespace.getText().split("\n");
      if (spaces.length > 1) {
        ws += whitespace.getText().split("\n")[1];
      }
    }
    return generateDocumentationContentStub(element, ws, checkReturn);
  }

  private static String generateContent(PyFunction function, String offset, String prefix, boolean checkReturn) {
    //TODO: this code duplicates PyDocstringGenerator in some parts

    final StringBuilder builder = new StringBuilder(offset);
    final TypeEvalContext context = TypeEvalContext.userInitiated(function.getContainingFile());
    PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
    final PyDecoratorList decoratorList = function.getDecoratorList();
    final PyDecorator classMethod = decoratorList == null ? null : decoratorList.findDecorator(PyNames.CLASSMETHOD);
    for (PyParameter p : PyUtil.getParameters(function, context)) {
      final String parameterName = p.getName();
      if (p.getText().equals(PyNames.CANONICAL_SELF) || parameterName == null) {
        continue;
      }
      if (classMethod != null && parameterName.equals(PyNames.CANONICAL_CLS)) continue;
      String argType = signature == null ? null : signature.getArgTypeQualifiedName(parameterName);

      if (argType == null) {
        builder.append(prefix);
        builder.append("param ");
        builder.append(parameterName);
        builder.append(": ");
        builder.append(offset);
      }
      if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB || argType != null) {
        builder.append(prefix);
        builder.append("type ");
        builder.append(parameterName);
        builder.append(": ");
        if (signature != null && argType != null) {
          builder.append(PySignatureUtil.getShortestImportableName(function, argType));
        }
        builder.append(offset);
      }
    }
    builder.append(generateRaiseOrReturn(function, offset, prefix, checkReturn));
    return builder.toString();
  }

  public static String generateRaiseOrReturn(PyFunction element, String offset, String prefix, boolean checkReturn) {
    StringBuilder builder = new StringBuilder();
    if (checkReturn) {
      RaiseVisitor visitor = new RaiseVisitor();
      PyStatementList statementList = element.getStatementList();
      if (statementList != null) {
        statementList.accept(visitor);
      }
      if (visitor.myHasReturn) {
        builder.append(prefix).append("return:").append(offset);
        if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
          builder.append(prefix).append("rtype:").append(offset);
        }
      }
      if (visitor.myHasRaise) {
        builder.append(prefix).append("raise");
        if (visitor.myRaiseTarget != null) {
          String raiseTarget = visitor.myRaiseTarget.getText();
          if (visitor.myRaiseTarget instanceof PyCallExpression) {
            final PyExpression callee = ((PyCallExpression)visitor.myRaiseTarget).getCallee();
            if (callee != null)
              raiseTarget = callee.getText();
          }
          builder.append(" ").append(raiseTarget);
        }
        builder.append(":").append(offset);
      }
    }
    else {
      builder.append(prefix).append("return:").append(offset);
      if (PyCodeInsightSettings.getInstance().INSERT_TYPE_DOCSTUB) {
        builder.append(prefix).append("rtype:").append(offset);
      }
    }
    return builder.toString();
  }

  private static class RaiseVisitor extends PyRecursiveElementVisitor {
    private boolean myHasRaise = false;
    private boolean myHasReturn = false;
    private PyExpression myRaiseTarget = null;

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      myHasRaise = true;
      final PyExpression[] expressions = node.getExpressions();
      if (expressions.length > 0) myRaiseTarget = expressions[0];
    }

    @Override
    public void visitPyReturnStatement(PyReturnStatement node) {
      myHasReturn = true;
    }
  }
}
