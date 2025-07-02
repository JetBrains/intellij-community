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
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableList;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.options.OptCheckbox;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonUiService;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.validation.PyCompatibilityVisitor;
import com.jetbrains.python.validation.UnsupportedFeaturesUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.*;

/**
 * User: catherine
 *
 * Inspection to detect code incompatibility with python versions
 */
public final class PyCompatibilityInspection extends PyInspection {

  public static final @NotNull List<String> BACKPORTED_PACKAGES = ImmutableList.<String>builder()
    .add("enum")
    .add("typing")
    .add("dataclasses")
    .build();

  public static final List<String> COMPATIBILITY_LIBS = Collections.singletonList("six");

  public static final int LATEST_INSPECTION_VERSION = 3;

  public static final @NotNull List<LanguageLevel> DEFAULT_PYTHON_VERSIONS = ImmutableList.of(LanguageLevel.PYTHON27, LanguageLevel.getLatest());

  public static final @NotNull List<LanguageLevel> SUPPORTED_LEVELS = StreamEx
    .of(LanguageLevel.values())
    .filter(v -> v == LanguageLevel.PYTHON27 || v.isAtLeast(LanguageLevel.PYTHON37))
    .toImmutableList();

  private static final @NotNull List<@NlsSafe String> SUPPORTED_IN_SETTINGS = ContainerUtil.map(SUPPORTED_LEVELS, LanguageLevel::toString);

  // Legacy DefaultJDOMExternalizer requires public fields for proper serialization
  public JDOMExternalizableStringList ourVersions = new JDOMExternalizableStringList();

  public PyCompatibilityInspection () {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourVersions.addAll(SUPPORTED_IN_SETTINGS);
    }
    else {
      ourVersions.addAll(ContainerUtil.map(DEFAULT_PYTHON_VERSIONS, LanguageLevel::toString));
    }
  }
  
  public static @Nullable PyCompatibilityInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    final String toolName = PyCompatibilityInspection.class.getSimpleName();
    return (PyCompatibilityInspection)inspectionProfile.getUnwrappedTool(toolName, element);
  }

  private List<LanguageLevel> updateVersionsToProcess() {
    List<LanguageLevel> result = new ArrayList<>();

    for (String version : ourVersions) {
      if (SUPPORTED_IN_SETTINGS.contains(version)) {
        LanguageLevel level = LanguageLevel.fromPythonVersion(version);
        result.add(level);
      }
    }
    return result;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    @SuppressWarnings("LanguageMismatch") OptCheckbox[] versionCheckboxes =
      ContainerUtil.map2Array(SUPPORTED_IN_SETTINGS, OptCheckbox.class, ver -> checkbox(ver, ver));
    return pane(group(PyPsiBundle.message("INSP.compatibility.check.for.compatibility.with.python.versions"), versionCheckboxes));
  }

  @Override
  public @NotNull OptionController getOptionController() {
    return OptionController.of(
      ourVersions::contains,
      (bindId, value) -> {
        boolean checked = (boolean)value;
        if (!checked) {
          ourVersions.remove(bindId);
        }
        else if (!ourVersions.contains(bindId)) {
          ourVersions.add(bindId);
        }
      });
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder, updateVersionsToProcess());
  }

  private static class Visitor extends PyCompatibilityVisitor {
    private final ProblemsHolder myHolder;
    private final Set<String> myUsedImports = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> myFromCompatibilityLibs = Collections.synchronizedSet(new HashSet<>());

    Visitor(ProblemsHolder holder, List<LanguageLevel> versionsToProcess) {
      super(versionsToProcess);
      myHolder = holder;
    }

    @Override
    protected void registerProblem(@NotNull PsiElement element,
                                   @NotNull TextRange range,
                                   @NotNull @InspectionMessage String message,
                                   boolean asError,
                                   LocalQuickFix @NotNull ... fixes) {
      if (element.getTextLength() == 0) {
        return;
      }

      range = range.shiftRight(-element.getTextRange().getStartOffset());
      myHolder.registerProblem(element, range, message, fixes);
    }

    @Override
    public void visitPyCallExpression(@NotNull PyCallExpression node) {
      super.visitPyCallExpression(node);
      PyExpression callee = node.getCallee();
      if (callee != null && importedFromCompatibilityLibs(callee)) {
        return;
      }

      final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
      final PsiElement resolvedCallee = Optional
        .ofNullable(callee)
        .map(PyExpression::getReference)
        .map(PsiReference::resolve)
        .map(e -> e instanceof PyClass ? ((PyClass)e).findInitOrNew(false, context) : e)
        .orElse(null);

      if (resolvedCallee instanceof PyFunction function) {
        final PyClass containingClass = function.getContainingClass();

        final String functionName = PyUtil.isConstructorLikeMethod(function) ? callee.getText() : function.getName();

        if (containingClass != null) {
          final String className = containingClass.getName();

          if (UnsupportedFeaturesUtil.CLASS_METHODS.containsKey(className)) {
            final Map<LanguageLevel, Set<String>> unsupportedMethods = UnsupportedFeaturesUtil.CLASS_METHODS.get(className);

            registerForAllMatchingVersions(level -> unsupportedMethods.getOrDefault(level, Collections.emptySet()).contains(functionName),
                                           PyPsiBundle.message("INSP.compatibility.feature.have.method", functionName),
                                           node);
          }
        }

        if (PyBuiltinCache.getInstance(function).isBuiltin(function) &&
            !"print".equals(functionName) &&
            !"exec".equals(functionName) &&
            !myUsedImports.contains(functionName)) {
          registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.BUILTINS.get(level).contains(functionName),
                                         PyPsiBundle.message("INSP.compatibility.feature.have.method", functionName),
                                         node);
        }
      }
      else if (resolvedCallee instanceof PyTargetExpression target) {

        if (!target.isQualified() &&
            PyNames.TYPE_LONG.equals(target.getName()) &&
            PyBuiltinCache.getInstance(resolvedCallee).isBuiltin(resolvedCallee)) {
          registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.BUILTINS.get(level).contains(PyNames.TYPE_LONG),
                                         PyPsiBundle.message("INSP.compatibility.feature.have.type.long"),
                                         node);
        }
      }
    }

    @Override
    public void visitPyImportElement(@NotNull PyImportElement importElement) {
      myUsedImports.add(importElement.getVisibleName());

      final PyIfStatement ifParent = PsiTreeUtil.getParentOfType(importElement, PyIfStatement.class);
      if (ifParent != null) return;

      final PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(importElement, PyTryExceptStatement.class);
      if (tryExceptStatement != null) {
        for (PyExceptPart part : tryExceptStatement.getExceptParts()) {
          final PyExpression exceptClass = part.getExceptClass();
          if (exceptClass != null && exceptClass.getText().equals("ImportError")) {
            return;
          }
        }
      }

      final QualifiedName qName = getImportedFullyQName(importElement);
      if (qName != null && !qName.matches("builtins") && !qName.matches("__builtin__")) {
        if (COMPATIBILITY_LIBS.contains(qName.getFirstComponent())) {
          myFromCompatibilityLibs.add(qName.getLastComponent());
        }
        final String moduleName = qName.toString();

        registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.MODULES.get(level).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName),
                                       PyPsiBundle.message("INSP.compatibility.feature.have.module", moduleName),
                                       importElement);
      }
    }

    private static @Nullable QualifiedName getImportedFullyQName(@NotNull PyImportElement importElement) {
      final QualifiedName importedQName = importElement.getImportedQName();
      if (importedQName == null) return null;

      final PyStatement containingImportStatement = importElement.getContainingImportStatement();
      final QualifiedName importSourceQName = containingImportStatement instanceof PyFromImportStatement
                                              ? ((PyFromImportStatement)containingImportStatement).getImportSourceQName()
                                              : null;

      return importSourceQName == null ? importedQName : importSourceQName.append(importedQName);
    }

    @Override
    public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
      super.visitPyFromImportStatement(node);

      if (node.getRelativeLevel() > 0) return;

      final QualifiedName name = node.getImportSourceQName();
      final PyReferenceExpression source = node.getImportSource();
      if (name != null && source != null) {
        final String moduleName = name.toString();

        registerForAllMatchingVersions(level -> UnsupportedFeaturesUtil.MODULES.get(level).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName),
                                       PyPsiBundle.message("INSP.compatibility.feature.have.module", name),
                                       source);
      }
    }

    @Override
    public void visitPyArgumentList(final @NotNull PyArgumentList node) { //PY-5588
      if (node.getParent() instanceof PyClass) {
        final boolean isPython2 = LanguageLevel.forElement(node).isPython2();
        if (isPython2 || myVersionsToProcess.stream().anyMatch(LanguageLevel::isPython2)) {
          Arrays
            .stream(node.getArguments())
            .filter(PyKeywordArgument.class::isInstance)
            .forEach(expression -> myHolder.registerProblem(expression,
                                                            PyPsiBundle.message("INSP.compatibility.this.syntax.available.only.since.py3"),
                                                            !isPython2
                                                            ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                                                            : ProblemHighlightType.GENERIC_ERROR));
        }
      }
    }

    @Override
    public void visitPyReferenceExpression(@NotNull PyReferenceExpression node) {
      super.visitPyElement(node);

      if (myVersionsToProcess.stream().anyMatch(LanguageLevel::isPy3K)) {
        final String nodeText = node.getText();

        if (nodeText.endsWith("iteritems") || nodeText.endsWith("iterkeys") || nodeText.endsWith("itervalues")) {
          final PyExpression qualifier = node.getQualifier();
          if (qualifier != null) {
            final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
            final PyType type = context.getType(qualifier);
            final PyClassType dictType = PyBuiltinCache.getInstance(node).getDictType();
            if (PyTypeChecker.match(dictType, type, context)) {
              //noinspection DialogTitleCapitalization
              registerProblem(node, PyPsiBundle.message("INSP.compatibility.old.dict.methods.not.available.in.py3"));
            }
          }
        }

        if (PyNames.BASESTRING.equals(nodeText)) {
          final PsiElement res = node.getReference().resolve();
          if (res != null) {
            final PsiFile file = res.getContainingFile();
            if (file != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile != null && ProjectRootManager.getInstance(node.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
                //noinspection DialogTitleCapitalization
                registerProblem(node, PyPsiBundle.message("INSP.compatibility.basestring.type.not.available.in.py3"));
              }
            }
            else {
              //noinspection DialogTitleCapitalization
              registerProblem(node, PyPsiBundle.message("INSP.compatibility.basestring.type.not.available.in.py3"));
            }
          }
        }
      }
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      super.visitPyTargetExpression(node);
      warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      super.visitPyClass(node);
      warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      super.visitPyFunction(node);
      warnAsyncAndAwaitAreBecomingKeywordsInPy37(node);
    }

    @Override
    protected boolean registerForLanguageLevel(@NotNull LanguageLevel level) {
      return level != LanguageLevel.forElement(myHolder.getFile());
    }

    private void warnAsyncAndAwaitAreBecomingKeywordsInPy37(@NotNull PsiNameIdentifierOwner nameIdentifierOwner) {
      final PsiElement nameIdentifier = nameIdentifierOwner.getNameIdentifier();

      if (nameIdentifier != null &&
          ArrayUtil.contains(nameIdentifierOwner.getName(), PyNames.AWAIT, PyNames.ASYNC) &&
          LanguageLevel.forElement(nameIdentifierOwner).isOlderThan(LanguageLevel.PYTHON37)) {
        registerForAllMatchingVersions(level -> level.isAtLeast(LanguageLevel.PYTHON37),
                                       PyPsiBundle.message("INSP.compatibility.feature.allow.async.and.await.as.names"),
                                       nameIdentifier,
                                       PythonUiService.getInstance().createPyRenameElementQuickFix(nameIdentifierOwner));
      }
    }

    private boolean importedFromCompatibilityLibs(@NotNull PyExpression callee) {
      if (callee instanceof PyQualifiedExpression) {
        QualifiedName qualifiedName = ((PyQualifiedExpression) callee).asQualifiedName();
        return qualifiedName != null && myFromCompatibilityLibs.contains(qualifiedName.getFirstComponent());
      }
      return myFromCompatibilityLibs.contains(callee.getName());
    }
  }
}
