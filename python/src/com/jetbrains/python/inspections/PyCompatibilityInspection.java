package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
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
              if (!"print".equals(name) && !myUsedImports.contains(name) && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
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
      if (shouldBeCompatibleWithPy2() || !isPy3) {
        for (final PyElement problemElement : problemElements)
          myHolder.registerProblem(problemElement, errorMessage, isPy3? ProblemHighlightType.GENERIC_ERROR_OR_WARNING :
                                                          ProblemHighlightType.GENERIC_ERROR);
      }
    }
  }
}