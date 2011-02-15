package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
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

  private List<LanguageLevel> myVersionsToProcess;

  public PyCompatibilityInspection () {
    super();
    if (ApplicationManager.getApplication().isUnitTestMode()) toVersion = LanguageLevel.PYTHON31.toString();
    myVersionsToProcess = new ArrayList<LanguageLevel>();
    updateVersionsToProcess();
  }

  @Override
  public boolean isEnabledByDefault() {
    return false;
  }

  private void updateVersionsToProcess() {
    myVersionsToProcess.clear();

    boolean add = false;
    for (String version : UnsupportedFeaturesUtil.ALL_LANGUAGE_LEVELS) {
      LanguageLevel level = LanguageLevel.fromPythonVersion(version);
      if (version.equals(fromVersion))
        add = true;
      if (version.equals(toVersion)) {
        myVersionsToProcess.add(level);
        add = false;
      }
      if (add)
        myVersionsToProcess.add(level);
    }
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
    updateVersionsToProcess();
    return new Visitor(holder, myVersionsToProcess);
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
  }
}
