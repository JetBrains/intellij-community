package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.validation.CompatibilityVisitor;
import com.jetbrains.python.validation.UnsupportedFeaturesUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/**
 * User: catherine
 *
 * Inspection to detect code incompatibility with python versions
 */
public class PyCompatibilityInspection extends PyInspection {
  public String fromVersion = LanguageLevel.PYTHON24.toString();
  public String toVersion = LanguageLevel.PYTHON27.toString();

  public PyCompatibilityInspection () {
    super();
    if (ApplicationManager.getApplication().isUnitTestMode()) toVersion = LanguageLevel.PYTHON31.toString();
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private List<LanguageLevel> updateVersionsToProcess() {
    List<LanguageLevel> result = new ArrayList<LanguageLevel>();

    boolean add = false;
    for (String version : UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS) {
      LanguageLevel level = LanguageLevel.fromPythonVersion(version);
      if (version.equals(fromVersion))
        add = true;
      if (version.equals(toVersion)) {
        result.add(level);
        add = false;
      }
      if (add)
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
    final JPanel versionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    final JComboBox fromComboBox = new JComboBox(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS);
    fromComboBox.setSelectedItem(fromVersion);
    final JComboBox toComboBox = new JComboBox(UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS);
    toComboBox.setSelectedItem(toVersion);

    fromComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        fromVersion = (String)cb.getSelectedItem();
      }
    });

    toComboBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JComboBox cb = (JComboBox)e.getSource();
        toVersion = (String)cb.getSelectedItem();
      }
    });

    versionPanel.add(new JLabel("Check for compatibility with python from"));
    versionPanel.add(fromComboBox);
    versionPanel.add(new JLabel("to"));
    versionPanel.add(toComboBox);
    return versionPanel;
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new Visitor(holder, updateVersionsToProcess());
  }

  private static class Visitor extends CompatibilityVisitor {
    private final ProblemsHolder myHolder;
    public Visitor(ProblemsHolder holder, List<LanguageLevel> versionsToProcess) {
      super(versionsToProcess);
      myHolder = holder;
    }

    @Override
    protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       @Nullable final LocalQuickFix quickFix, final boolean asError){
      if (element == null || element.getTextLength() == 0){
          return;
      }
      if (quickFix != null)
        myHolder.registerProblem(element, message, quickFix);
      else
        myHolder.registerProblem(element, message);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);

      int len = 0;
      StringBuilder message = new StringBuilder("Python version ");
      final PyExpression callee = node.getCallee();
      assert callee != null;
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        PsiReference reference = callee.getReference();
        if (reference != null) {
          PsiElement resolved = reference.resolve();
          ProjectFileIndex ind = ProjectRootManager.getInstance(callee.getProject()).getFileIndex();
          final String name = callee.getText();
          if (resolved != null) {
            PsiFile file = resolved.getContainingFile();
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null && ind.isInLibraryClasses(virtualFile)) {
              if (!"print".equals(name) && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
                len = appendLanguageLevel(message, len, languageLevel);
              }
            }
          }
          //else {
          //  if (!name.equals("print") && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
          //    len = appendLanguageLevel(message, len, languageLevel);
          //  }
          //}
        }
      }
      commonRegisterProblem(message, " not have method " + callee.getText(),
                            len, node, null, false);
    }

    @Override
    public void visitPyImportElement(PyImportElement importElement) {
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
          if (part.getExceptClass() != null && part.getExceptClass().getText().equals("ImportError")) {
            return;
          }
        }
      }

      PyFromImportStatement fromImportStatement = PsiTreeUtil.getParentOfType(importElement, PyFromImportStatement.class);
      if (fromImportStatement != null)
        return;

      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        final PyQualifiedName qName = importElement.getImportedQName();
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
      int len = 0;
      StringBuilder message = new StringBuilder("Python version ");
      PyQualifiedName name = node.getImportSourceQName();
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
      if (compatibleWithPy2() || !isPy3) {
        for (final PyElement problemElement : problemElements)
          myHolder.registerProblem(problemElement, errorMessage, isPy3? ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
                                                          ProblemHighlightType.GENERIC_ERROR);
      }
    }
  }
}