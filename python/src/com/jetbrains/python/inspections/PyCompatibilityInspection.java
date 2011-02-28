package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
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
    final JPanel versionPanel = new JPanel();

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

    versionPanel.add(new JLabel("Check for compatibility with python from"), BorderLayout.WEST);
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

    protected final void registerProblem(@Nullable final PsiElement element,
                                       @NotNull final String message,
                                       final LocalQuickFix quickFix, boolean asError){
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
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        PyExpression callee = node.getCallee();
        assert callee != null;
        PsiReference reference = callee.getReference();
        if (reference != null) {
          PsiElement resolved = reference.resolve();
          ProjectFileIndex ind = ProjectRootManager.getInstance(callee.getProject()).getFileIndex();
          final String name = callee.getText();
          if (resolved != null) {
            PsiFile file = resolved.getContainingFile();
            if (file != null && ind.isInLibraryClasses(file.getVirtualFile())) {
              if (!name.equals("print") && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
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
      commonRegisterProblem(message, " not have method " + node.getCallee().getText(),
                            len, node, null, false);
    }
    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      super.visitPyImportStatement(node);
      PyImportElement[] importElements = node.getImportElements();
      int len = 0;
      String moduleName = "";
      StringBuilder message = new StringBuilder("Python version ");
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        for (PyImportElement importElement : importElements) {
          final PyQualifiedName qName = importElement.getImportedQName();
          if (qName != null && !qName.matches("builtins") && !qName.matches("__builtin__")) {
            moduleName = qName.toString();
            if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(moduleName))
              len = appendLanguageLevel(message, len, languageLevel);
          }
        }
      }
      commonRegisterProblem(message, " not have module " + moduleName, len, node, null);
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      super.visitPyFromImportStatement(node);
      int len = 0;
      StringBuilder message = new StringBuilder("Python version ");
      PyReferenceExpression importSource  = node.getImportSource();
      if (importSource != null) {
        String name = importSource.getText();
        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel languageLevel = myVersionsToProcess.get(i);
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(name)) {
            len = appendLanguageLevel(message, len, languageLevel);
          }
        }
        commonRegisterProblem(message, " not have module " + name,
                              len, node, null, false);
      }
    }
  }
}
