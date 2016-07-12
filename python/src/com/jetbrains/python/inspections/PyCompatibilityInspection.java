/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ide.util.ElementsChooser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.validation.CompatibilityVisitor;
import com.jetbrains.python.validation.UnsupportedFeaturesUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect code incompatibility with python versions
 */
public class PyCompatibilityInspection extends PyInspection {
  public static List<String> BACKPORTED_PACKAGES = ImmutableList.<String>builder()
    .add("enum")
    .add("typing")
    .build();

  public static final int LATEST_INSPECTION_VERSION = 1;
  public static final List<LanguageLevel> DEFAULT_PYTHON_VERSIONS = ImmutableList.of(LanguageLevel.PYTHON27, LanguageLevel.getLatest());

  // Legacy DefaultJDOMExternalizer requires public fields for proper serialization
  public JDOMExternalizableStringList ourVersions = new JDOMExternalizableStringList();

  public PyCompatibilityInspection () {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourVersions.addAll(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS);
    }
    else {
      ourVersions.addAll(ContainerUtil.map(DEFAULT_PYTHON_VERSIONS, LanguageLevel::toString));
    }
  }
  
  @Nullable
  public static PyCompatibilityInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    final String toolName = PyCompatibilityInspection.class.getSimpleName();
    return (PyCompatibilityInspection)inspectionProfile.getUnwrappedTool(toolName, element);
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private List<LanguageLevel> updateVersionsToProcess() {
    List<LanguageLevel> result = new ArrayList<>();

    for (String version : ourVersions) {
      LanguageLevel level = LanguageLevel.fromPythonVersion(version);
      result.add(level);
    }
    return result;
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.compatibility");
  }

  @Override
  public JComponent createOptionsPanel() {
    final ElementsChooser<String> chooser = new ElementsChooser<>(true);
    chooser.setElements(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS, false);
    chooser.markElements(ourVersions);
    chooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<String>() {
      @Override
      public void elementMarkChanged(String element, boolean isMarked) {
        ourVersions.clear();
        ourVersions.addAll(chooser.getMarkedElements());
      }
    });
    final JPanel versionPanel = new JPanel(new BorderLayout());
    JLabel label = new JLabel("Check for compatibility with python versions:");
    label.setLabelFor(chooser);
    versionPanel.add(label, BorderLayout.PAGE_START);
    versionPanel.add(chooser);
    return versionPanel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder, updateVersionsToProcess());
  }

  private static class Visitor extends CompatibilityVisitor {
    private final ProblemsHolder myHolder;
    private Set<String> myUsedImports = Collections.synchronizedSet(new HashSet<String>());

    public Visitor(ProblemsHolder holder, List<LanguageLevel> versionsToProcess) {
      super(versionsToProcess);
      myHolder = holder;
    }

    @Override
    protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       @Nullable final LocalQuickFix quickFix, final boolean asError) {
      if (element == null) return;
      registerProblem(element, element.getTextRange(), message, quickFix, asError);
    }

    @Override
    protected void registerProblem(@NotNull final PsiElement element, @NotNull TextRange range, String message,
                                   @Nullable LocalQuickFix quickFix, boolean asError) {
      if (element.getTextLength() == 0) {
        return;
      }
      range = range.shiftRight(-element.getTextRange().getStartOffset());
      if (quickFix != null)
        myHolder.registerProblem(element, range, message, quickFix);
      else
        myHolder.registerProblem(element, range, message);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);

      final Optional<PyFunction> optionalFunction = Optional
        .ofNullable(node.getCallee())
        .map(PyExpression::getReference)
        .map(PsiReference::resolve)
        .filter(PyFunction.class::isInstance)
        .map(PyFunction.class::cast);

      if (optionalFunction.isPresent()) {
        final PyFunction function = optionalFunction.get();
        final PyClass containingClass = function.getContainingClass();
        final String originalFunctionName = function.getName();

        final StringBuilder message = containingClass != null && !PyNames.INIT.equals(originalFunctionName)
                                      ? new StringBuilder("Class " + containingClass.getName() + " in python version ")
                                      : new StringBuilder("Python version ");

        final String functionName = containingClass != null && PyNames.INIT.equals(originalFunctionName)
                                    ? node.getCallee().getText()
                                    : originalFunctionName;

        int len = 0;

        if (containingClass != null) {
          final String className = containingClass.getName();

          if (UnsupportedFeaturesUtil.CLASS_METHODS.containsKey(className)) {
            final Map<LanguageLevel, Set<String>> unsupportedMethods = UnsupportedFeaturesUtil.CLASS_METHODS.get(className);
            for (LanguageLevel languageLevel : myVersionsToProcess) {
              if (unsupportedMethods.getOrDefault(languageLevel, Collections.emptySet()).contains(functionName)) {
                len = appendLanguageLevel(message, len, languageLevel);
              }
            }
          }
        }

        if (PyBuiltinCache.getInstance(function).isBuiltin(function) &&
            !"print".equals(functionName) &&
            !"exec".equals(functionName) &&
            !myUsedImports.contains(functionName)) {
          for (LanguageLevel languageLevel : myVersionsToProcess) {
            if (UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(functionName)) {
              len = appendLanguageLevel(message, len, languageLevel);
            }
          }
        }

        commonRegisterProblem(message, " not have method " + functionName, len, node, null, false);
      }
    }

    @Override
    public void visitPyImportElement(PyImportElement importElement) {
      myUsedImports.add(importElement.getVisibleName());
      PyIfStatement ifParent = PsiTreeUtil.getParentOfType(importElement, PyIfStatement.class);
      if (ifParent != null)
        return;
      int len = 0;
      String moduleName = "";
      StringBuilder message = new StringBuilder("Python version ");

      PyTryExceptStatement tryExceptStatement = PsiTreeUtil.getParentOfType(importElement, PyTryExceptStatement.class);
      if (tryExceptStatement != null) {
        PyExceptPart[] parts = tryExceptStatement.getExceptParts();
        for (PyExceptPart part : parts) {
          final PyExpression exceptClass = part.getExceptClass();
          if (exceptClass != null && exceptClass.getText().equals("ImportError")) {
            return;
          }
        }
      }

      final PyFromImportStatement fromImportStatement = PsiTreeUtil.getParentOfType(importElement, PyFromImportStatement.class);
      if (fromImportStatement != null) {
        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel languageLevel = myVersionsToProcess.get(i);
          final QualifiedName qName = importElement.getImportedQName();
          final QualifiedName sourceQName = fromImportStatement.getImportSourceQName();
          if (qName != null && sourceQName != null && qName.matches("unicode_literals") &&
              sourceQName.matches("__future__") && languageLevel.isOlderThan(LanguageLevel.PYTHON26)) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
        commonRegisterProblem(message, " not have unicode_literals in __future__ module", len, importElement, null);
        return;
      }

      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        final QualifiedName qName = importElement.getImportedQName();
        if (qName != null && !qName.matches("builtins") && !qName.matches("__builtin__")) {
          moduleName = qName.toString();
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName)) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
      }
      commonRegisterProblem(message, " not have module " + moduleName, len, importElement, null);
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      super.visitPyFromImportStatement(node);
      if (node.getRelativeLevel() > 0) return;
      int len = 0;
      StringBuilder message = new StringBuilder("Python version ");
      QualifiedName name = node.getImportSourceQName();
      PyReferenceExpression source = node.getImportSource();
      if (name != null) {
        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel languageLevel = myVersionsToProcess.get(i);
          final String moduleName = name.toString();
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(moduleName) && !BACKPORTED_PACKAGES.contains(moduleName)) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
        commonRegisterProblem(message, " not have module " + name,
                              len, source, null, false);
      }
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList node) { //PY-5588
      final List<PyElement> problemElements = new ArrayList<>();
      if (node.getParent() instanceof PyClass) {
        for (final PyExpression expression : node.getArguments()) {
          if (expression instanceof PyKeywordArgument)
            problemElements.add(expression);
        }
      }
      final String errorMessage = "This syntax available only since py3";
      final boolean isPy3 = LanguageLevel.forElement(node).isPy3K();
      if (shouldBeCompatibleWithPy2() || !isPy3) {
        for (final PyElement problemElement : problemElements)
          myHolder.registerProblem(problemElement, errorMessage, isPy3? ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
                                                          ProblemHighlightType.GENERIC_ERROR);
      }
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
      super.visitPyElement(node);
      if (shouldBeCompatibleWithPy3()) {
        final TypeEvalContext context = TypeEvalContext.codeAnalysis(node.getProject(), node.getContainingFile());
        final String nodeText = node.getText();
        if (nodeText.endsWith("iteritems") || nodeText.endsWith("iterkeys") || nodeText.endsWith("itervalues")) {
          final PyExpression qualifier = node.getQualifier();
          if (qualifier != null) {
            final PyType type = context.getType(qualifier);
            final PyClassType dictType = PyBuiltinCache.getInstance(node).getDictType();
            if (PyTypeChecker.match(dictType, type, context)) {
              registerProblem(node, "dict.iterkeys(), dict.iteritems() and dict.itervalues() methods are not available in py3");
            }
          }
        }

        if (PyNames.BASESTRING.equals(nodeText)) {
          PsiElement res = node.getReference().resolve();
          if (res != null) {
            ProjectFileIndex ind = ProjectRootManager.getInstance(node.getProject()).getFileIndex();
            PsiFile file = res.getContainingFile();
            if (file != null ) {
              final VirtualFile virtualFile = file.getVirtualFile();
                if (virtualFile != null && ind.isInLibraryClasses(virtualFile)) {
                registerProblem(node, "basestring type is not available in py3");
              }
            } else {
              registerProblem(node, "basestring type is not available in py3");
            }
          }
        }
      }
    }
  }
}