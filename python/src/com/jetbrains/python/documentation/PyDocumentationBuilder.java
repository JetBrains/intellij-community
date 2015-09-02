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
package com.jetbrains.python.documentation;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.LineTokenizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.console.PyConsoleUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.resolve.RootVisitor;
import com.jetbrains.python.psi.resolve.RootVisitorHost;
import com.jetbrains.python.psi.types.*;
import com.jetbrains.python.toolbox.ChainIterable;
import com.jetbrains.python.toolbox.Maybe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.documentation.DocumentationBuilderKit.*;

class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private ChainIterable<String> myResult;
  private final ChainIterable<String> myProlog;      // sequence for reassignment info, etc
  private final ChainIterable<String> myBody;        // sequence for doc string
  private final ChainIterable<String> myEpilog;      // sequence for doc "copied from" notices and such

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");

  public PyDocumentationBuilder(PsiElement element, PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myResult = new ChainIterable<String>();
    myProlog = new ChainIterable<String>();
    myBody = new ChainIterable<String>();
    myEpilog = new ChainIterable<String>();

    myResult.add(myProlog).addWith(TagCode, myBody).add(myEpilog); // pre-assemble; then add stuff to individual cats as needed
    myResult = wrapInTag("html", wrapInTag("body", myResult));
  }

  @Nullable
  public String build() {
    final ChainIterable<String> reassignCat = new ChainIterable<String>(); // sequence for reassignment info, etc
    PsiElement followed = resolveToDocStringOwner(reassignCat);

    // check if we got a property ref.
    // if so, element is an accessor, and originalElement if an identifier
    // TODO: use messages from resources!
    PyClass cls;
    PsiElement outer = null;
    boolean isProperty = false;
    String accessorKind = "None";
    final TypeEvalContext context = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
    if (myOriginalElement != null) {
      final String elementName = myOriginalElement.getText();
      if (PyUtil.isPythonIdentifier(elementName)) {
        outer = myOriginalElement.getParent();
        if (outer instanceof PyQualifiedExpression) {
          final PyExpression qual = ((PyQualifiedExpression)outer).getQualifier();
          if (qual != null) {
            final PyType type = context.getType(qual);
            if (type instanceof PyClassType) {
              cls = ((PyClassType)type).getPyClass();
              final Property property = cls.findProperty(elementName, true, null);
              if (property != null) {
                isProperty = true;
                final AccessDirection dir = AccessDirection.of((PyElement)outer);
                final Maybe<PyCallable> accessor = property.getByDirection(dir);
                myProlog
                  .addItem("property ").addWith(TagBold, $().addWith(TagCode, $(elementName)))
                  .addItem(" of ").add(PythonDocumentationProvider.describeClass(cls, TagCode, true, true));
                if (accessor.isDefined() && property.getDoc() != null) {
                  myBody.addItem(": ").addItem(property.getDoc()).addItem(BR);
                }
                else {
                  final PyCallable getter = property.getGetter().valueOrNull();
                  if (getter != null && getter != myElement && getter instanceof PyFunction) {
                    // not in getter, getter's doc comment may be useful
                    final PyStringLiteralExpression docstring = ((PyFunction)getter).getDocStringExpression();
                    if (docstring != null) {
                      myProlog
                        .addItem(BR).addWith(TagItalic, $("Copied from getter:")).addItem(BR)
                        .addItem(docstring.getStringValue());
                    }
                  }
                  myBody.addItem(BR);
                }
                myBody.addItem(BR);
                if (accessor.isDefined() && accessor.value() == null) followed = null;
                if (dir == AccessDirection.READ) {
                  accessorKind = "Getter";
                }
                else if (dir == AccessDirection.WRITE) {
                  accessorKind = "Setter";
                }
                else {
                  accessorKind = "Deleter";
                }
                if (followed != null) myEpilog.addWith(TagSmall, $(BR, BR, accessorKind, " of property")).addItem(BR);
              }
            }
          }
        }
      }
    }

    if (myProlog.isEmpty() && !isProperty && !isAttribute()) {
      myProlog.add(reassignCat);
    }

    // now followed may contain a doc string
    if (followed instanceof PyDocStringOwner) {
      String docString = null;
      final PyStringLiteralExpression docExpr = ((PyDocStringOwner)followed).getDocStringExpression();
      if (docExpr != null) docString = docExpr.getStringValue();
      // doc of what?
      if (followed instanceof PyClass) {
        cls = (PyClass)followed;
        myBody.add(PythonDocumentationProvider.describeDecorators(cls, TagItalic, BR, LCombUp));
        myBody.add(PythonDocumentationProvider.describeClass(cls, TagBold, true, false));
      }
      else if (followed instanceof PyFunction) {
        final PyFunction fun = (PyFunction)followed;
        if (!isProperty) {
          cls = fun.getContainingClass();
          if (cls != null) {
            myBody.addWith(TagSmall, PythonDocumentationProvider.describeClass(cls, TagCode, true, true)).addItem(BR).addItem(BR);
          }
        }
        else {
          cls = null;
        }
        myBody
          .add(PythonDocumentationProvider.describeDecorators(fun, TagItalic, BR, LCombUp))
          .add(PythonDocumentationProvider.describeFunction(fun, TagBold, LCombUp));
        if (docString == null) {
          addInheritedDocString(fun, cls);
        }
      }
      else if (followed instanceof PyFile) {
        addModulePath((PyFile)followed);
      }
      if (docString != null) {
        myBody.addItem(BR);
        addFormattedDocString(myElement, docString, myBody, myEpilog);
      }
    }
    else if (isProperty) {
      // if it was a normal accessor, ti would be a function, handled by previous branch
      final String accessorMessage;
      if (followed != null) {
        accessorMessage = "Declaration: ";
      }
      else {
        accessorMessage = accessorKind + " is not defined.";
      }
      myBody.addWith(TagItalic, $(accessorMessage)).addItem(BR);
      if (followed != null) myBody.addItem(combUp(PyUtil.getReadableRepr(followed, false)));
    }
    else if (isAttribute()) {
      addAttributeDoc();
    }
    else if (followed instanceof PyNamedParameter) {
      myBody.addItem(combUp("Parameter " + PyUtil.getReadableRepr(followed, false)));
      final boolean typeFromDocstringAdded = addTypeAndDescriptionFromDocstring((PyNamedParameter)followed);
      if (outer instanceof PyExpression) {
        final PyType type = context.getType((PyExpression)outer);
        if (type != null) {
          String s = null;
          if (type instanceof PyDynamicallyEvaluatedType) {
            if (!typeFromDocstringAdded) {
              //don't add dynamic type if docstring type specified
              s = "\nDynamically inferred type: ";
            }
          }
          else {
            if (outer.getReference() != null) {
              final PsiElement target = outer.getReference().resolve();

              if (target instanceof PyTargetExpression &&
                  ((PyTargetExpression)target).getName().equals(((PyNamedParameter)followed).getName())) {
                s = "\nReassigned value has type: ";
              }
            }
          }
          if (s == null && !typeFromDocstringAdded) {
            s = "\nInferred type: ";
          }
          if (s != null) {
            myBody
              .addItem(combUp(s));
            PythonDocumentationProvider.describeTypeWithLinks(myBody, followed, type, context);
          }
        }
      }
    }
    else if (followed != null && outer instanceof PyReferenceExpression) {
      myBody.addItem(combUp("\nInferred type: "));
      PythonDocumentationProvider.describeExpressionTypeWithLinks(myBody, (PyReferenceExpression)outer, context);
    }
    if (myBody.isEmpty() && myEpilog.isEmpty()) {
      return null; // got nothing substantial to say!
    }
    else {
      return myResult.toString();
    }
  }

  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  @Nullable
  private PsiElement resolveToDocStringOwner(@NotNull ChainIterable<String> prologCat) {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression) {
      final String targetName = myElement.getText();
      //prologCat.add(TagSmall.apply($("Assigned to ", element.getText(), BR)));
      prologCat.addWith(TagSmall, $(PyBundle.message("QDOC.assigned.to.$0", targetName)).addItem(BR));
      final PyExpression assignedValue = ((PyTargetExpression)myElement).findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression) {
        final PsiElement resolved = resolveWithoutImplicits((PyReferenceExpression)assignedValue);
        if (resolved != null) {
          return resolved;
        }
      }
      return assignedValue;
    }
    if (myElement instanceof PyReferenceExpression) {
      //prologCat.add(TagSmall.apply($("Assigned to ", element.getText(), BR)));
      prologCat.addWith(TagSmall, $(PyBundle.message("QDOC.assigned.to.$0", myElement.getText())).addItem(BR));
      return resolveWithoutImplicits((PyReferenceExpression)myElement);
    }
    // it may be a call to a standard wrapper
    if (myElement instanceof PyCallExpression) {
      final PyCallExpression call = (PyCallExpression)myElement;
      final Pair<String, PyFunction> wrapInfo = PyCallExpressionHelper.interpretAsModifierWrappingCall(call, myOriginalElement);
      if (wrapInfo != null) {
        final String wrapperName = wrapInfo.getFirst();
        final PyFunction wrappedFunc = wrapInfo.getSecond();
        //prologCat.addWith(TagSmall, $("Wrapped in ").addWith(TagCode, $(wrapperName)).add(BR));
        prologCat.addWith(TagSmall, $(PyBundle.message("QDOC.wrapped.in.$0", wrapperName)).addItem(BR));
        return wrappedFunc;
      }
    }
    return myElement;
  }

  private static PsiElement resolveWithoutImplicits(@NotNull PyReferenceExpression element) {
    final QualifiedResolveResult resolveResult = element.followAssignmentsChain(PyResolveContext.noImplicits());
    return resolveResult.isImplicit() ? null : resolveResult.getElement();
  }

  private void addInheritedDocString(@NotNull PyFunction fun, @Nullable PyClass cls) {
    boolean notFound = true;
    final String methName = fun.getName();
    if (cls != null && methName != null) {
      final boolean isConstructor = PyNames.INIT.equals(methName);
      // look for inherited and its doc
      Iterable<PyClass> classes = cls.getAncestorClasses(null);
      if (isConstructor) {
        // look at our own class again and maybe inherit class's doc
        classes = new ChainIterable<PyClass>(cls).add(classes);
      }
      for (PyClass ancestor : classes) {
        PyStringLiteralExpression docElt = null;
        PyFunction inherited = null;
        boolean isFromClass = false;
        if (isConstructor) docElt = cls.getDocStringExpression();
        if (docElt != null) {
          isFromClass = true;
        }
        else {
          inherited = ancestor.findMethodByName(methName, false);
        }
        if (inherited != null) {
          docElt = inherited.getDocStringExpression();
        }
        if (docElt != null) {
          final String inheritedDoc = docElt.getStringValue();
          if (inheritedDoc.length() > 1) {
            myEpilog.addItem(BR).addItem(BR);
            final String ancestorName = ancestor.getName();
            final String marker =
              (cls == ancestor) ? PythonDocumentationProvider.LINK_TYPE_CLASS : PythonDocumentationProvider.LINK_TYPE_PARENT;
            final String ancestorLink =
              $().addWith(new LinkWrapper(marker + ancestorName), $(ancestorName)).toString();
            if (isFromClass) {
              myEpilog.addItem(PyBundle.message("QDOC.copied.from.class.$0", ancestorLink));
            }
            else {
              myEpilog.addItem(PyBundle.message("QDOC.copied.from.$0.$1", ancestorLink, methName));
            }
            myEpilog.addItem(BR).addItem(BR);
            final ChainIterable<String> formatted = new ChainIterable<String>();
            final ChainIterable<String> unformatted = new ChainIterable<String>();
            addFormattedDocString(fun, inheritedDoc, formatted, unformatted);
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
        if (PyNames.UnderscoredAttributes.contains(methName)) {
          addPredefinedMethodDoc(fun, methName);
        }
      }
    }
  }

  private void addPredefinedMethodDoc(@NotNull PyFunction fun, String methName) {
    final PyClassType objtype = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objtype != null) {
      final PyClass objcls = objtype.getPyClass();
      final PyFunction objUnderscored = objcls.findMethodByName(methName, false);
      if (objUnderscored != null) {
        final PyStringLiteralExpression predefinedDocExpr = objUnderscored.getDocStringExpression();
        final String predefinedDoc = predefinedDocExpr != null ? predefinedDocExpr.getStringValue() : null;
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
    final List<String> result = new ArrayList<String>();
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
   * @return true if type from docstring was added
   */
  private boolean addTypeAndDescriptionFromDocstring(@NotNull PyNamedParameter parameter) {
    final PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      final String docString = PyPsiUtils.strValue(function.getDocStringExpression());
      final Pair<String, String> typeAndDescr = getTypeAndDescr(docString, parameter);

      final String type = typeAndDescr.first;
      final String desc = typeAndDescr.second;

      if (type != null) {
        final PyType pyType = PyTypeParser.getTypeByName(parameter, type);
        if (pyType instanceof PyClassType) {
          myBody.addItem(": ").
            addWith(new LinkWrapper(PythonDocumentationProvider.LINK_TYPE_PARAM),
                    $(pyType.getName()));
        }
        else {
          myBody.addItem(": ").addItem(type);
        }
      }

      if (desc != null) {
        myEpilog.addItem(BR).addItem(desc);
      }

      return type != null;
    }

    return false;
  }

  private static Pair<String, String> getTypeAndDescr(String docString, @NotNull PyNamedParameter followed) {
    final StructuredDocString structuredDocString = docString != null ? DocStringUtil.parse(docString, followed) : null;
    String type = null;
    String desc = null;
    if (structuredDocString != null) {
      final String name = followed.getName();
      type = structuredDocString.getParamType(name);

      desc = structuredDocString.getParamDescription(name);
    }

    return Pair.create(type, desc);
  }

  private void addAttributeDoc() {
    final PyClass cls = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
    assert cls != null;
    final String type = PyUtil.isInstanceAttribute((PyExpression)myElement) ? "Instance attribute " : "Class attribute ";
    myProlog
      .addItem(type).addWith(TagBold, $().addWith(TagCode, $(((PyTargetExpression)myElement).getName())))
      .addItem(" of class ").addWith(PythonDocumentationProvider.LinkMyClass, $().addWith(TagCode, $(cls.getName()))).addItem(BR);

    final String docString = ((PyTargetExpression)myElement).getDocStringValue();
    if (docString != null) {
      addFormattedDocString(myElement, docString, myBody, myEpilog);
    }
  }

  public static String[] removeCommonIndentation(String docstring) {
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
    final List<String> result = new ArrayList<String>();
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
      final String path = file.getPath();
      final RootFinder finder = new RootFinder(path);
      RootVisitorHost.visitRoots(followed, finder);
      final String rootPath = finder.getResult();
      if (rootPath != null) {
        final String afterPart = path.substring(rootPath.length());
        myProlog.addWith(TagSmall, $(rootPath).addWith(TagBold, $(afterPart)));
      }
      else {
        myProlog.addWith(TagSmall, $(path));
      }
    }
  }

  private static class RootFinder implements RootVisitor {
    private String myResult;
    private final String myPath;

    private RootFinder(String path) {
      myPath = path;
    }

    public boolean visitRoot(@NotNull VirtualFile root, Module module, Sdk sdk, boolean isModuleSource) {
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
