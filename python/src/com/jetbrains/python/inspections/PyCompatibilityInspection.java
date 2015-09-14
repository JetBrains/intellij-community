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
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.QualifiedName;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
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
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect code incompatibility with python versions
 */
public class PyCompatibilityInspection extends PyInspection {

  public JDOMExternalizableStringList ourVersions = new JDOMExternalizableStringList();

  public PyCompatibilityInspection () {
    super();
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      ourVersions.addAll(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS);
    }
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private List<LanguageLevel> updateVersionsToProcess() {
    List<LanguageLevel> result = new ArrayList<LanguageLevel>();

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
    final JPanel versionPanel = new JPanel(new BorderLayout());
    final JBList list = new JBList(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS);

    JLabel label = new JLabel("Check for compatibility with python versions:");
    label.setLabelFor(list);
    versionPanel.add(label, BorderLayout.PAGE_START);
    list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    JBScrollPane scrollPane = new JBScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                         ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

    versionPanel.add(scrollPane);

    int[] indices = new int[ourVersions.size()];
    for (int i = 0; i != ourVersions.size(); ++i) {
      String s = ourVersions.get(i);
      indices[i] = UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS.indexOf(s);
    }

    list.setSelectedIndices(indices);
    list.setCellRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object o, int i, boolean b, boolean b2) {
        return super
          .getListCellRendererComponent(list, "Python " + o, i, b, b2);
      }
    });
    list.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent event) {
        ourVersions.clear();
        for (Object value : list.getSelectedValues()) {
          ourVersions.add((String)value);
        }
      }
    });

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

      int len = 0;
      StringBuilder message = new StringBuilder("Python version ");
      final PyExpression callee = node.getCallee();
      assert callee != null;
      PsiReference reference = callee.getReference();
      if (reference != null) {
        PsiElement resolved = reference.resolve();
        ProjectFileIndex ind = ProjectRootManager.getInstance(callee.getProject()).getFileIndex();
        if (resolved instanceof PyFunction) {
          String name = ((PyFunction)resolved).getName();
          final PyClass containingClass = ((PyFunction)resolved).getContainingClass();
          if (containingClass != null) {
            if (PyNames.INIT.equals(name))
              name = callee.getText();
            else
              message = new StringBuilder("Class " + containingClass.getName() + " in python version ");
            for (int i = 0; i != myVersionsToProcess.size(); ++i) {
              LanguageLevel languageLevel = myVersionsToProcess.get(i);
              if (UnsupportedFeaturesUtil.CLASS_METHODS.containsKey(containingClass.getName())) {
                final Map<LanguageLevel, Set<String>> map = UnsupportedFeaturesUtil.CLASS_METHODS.get(containingClass.getName());
                final Set<String> unsupportedMethods = map.get(languageLevel);
                if (unsupportedMethods != null && unsupportedMethods.contains(name))
                  len = appendLanguageLevel(message, len, languageLevel);
              }
            }
          }
          for (int i = 0; i != myVersionsToProcess.size(); ++i) {
            LanguageLevel languageLevel = myVersionsToProcess.get(i);
            if (PyBuiltinCache.getInstance(resolved).isBuiltin(resolved)) {
              if (!"print".equals(name) && !myUsedImports.contains(name) && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
                len = appendLanguageLevel(message, len, languageLevel);
              }
            }
          }
          commonRegisterProblem(message, " not have method " + name, len, node, null, false);
        }
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
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(moduleName)) {
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
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(name.toString())) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
        commonRegisterProblem(message, " not have module " + name,
                              len, source, null, false);
      }
    }

    @Override
    public void visitPyArgumentList(final PyArgumentList node) { //PY-5588
      final List<PyElement> problemElements = new ArrayList<PyElement>();
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