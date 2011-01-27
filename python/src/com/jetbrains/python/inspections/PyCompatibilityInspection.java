package com.jetbrains.python.inspections;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.util.containers.HashSet;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.actions.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.validation.UnsupportedFeaturesUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;

/**
 * User: catherine
 *
 * Inspection to detect code incompatibility with python versions
 */
public class PyCompatibilityInspection extends PyInspection {
  public String fromVersion = LanguageLevel.PYTHON24.toString();
  public String toVersion = LanguageLevel.PYTHON27.toString();

  private Vector<LanguageLevel> myVersionsToProcess;

  public PyCompatibilityInspection () {
    super();
    if (ApplicationManager.getApplication().isUnitTestMode()) toVersion = LanguageLevel.PYTHON31.toString();
    myVersionsToProcess = new Vector<LanguageLevel>();
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


  private static class Visitor extends PyInspectionVisitor {
    Vector<LanguageLevel> myVersionsToProcess;
    private String myCommonMessage = "Python versions ";

    public Visitor(final ProblemsHolder holder, Vector<LanguageLevel> versionsToProcess) {
      super(holder);
      myVersionsToProcess = versionsToProcess;
    }

    @Override
    public void visitPyDictCompExpression(PyDictCompExpression node) {
      super.visitPyDictCompExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (!languageLevel.supportsSetLiterals()) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel.toString());
          hasProblem = true;
        }
      }
      message.append(" do not support dictionary comprehensions");
      if (hasProblem)
        registerProblem(node, message.toString(), new ConvertDictCompQuickFix());
    }

    @Override
    public void visitPySetLiteralExpression(PySetLiteralExpression node) {
      super.visitPySetLiteralExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (!languageLevel.supportsSetLiterals()) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel.toString());
          hasProblem = true;
        }
      }
      message.append(" do not support set literal expressions");
      if (hasProblem)
        registerProblem(node, message.toString(), new ConvertSetLiteralQuickFix());
    }

    @Override
    public void visitPySetCompExpression(PySetCompExpression node) {
      super.visitPySetCompExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (!languageLevel.supportsSetLiterals()) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel.toString());
          hasProblem = true;
        }
      }
      message.append(" do not support set comprehensions");
      if (hasProblem)
        registerProblem(node, message.toString());
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
      super.visitPyExceptBlock(node);
      PyExpression exceptClass = node.getExceptClass();
      if (exceptClass != null) {
        if (myVersionsToProcess.contains(LanguageLevel.PYTHON24) || myVersionsToProcess.contains(LanguageLevel.PYTHON25)) {
          PsiElement element = exceptClass.getNextSibling();
          while (element instanceof PsiWhiteSpace) {
            element = element.getNextSibling();
          }
          if (element != null && "as".equals(element.getText())) {
            registerProblem(node, myCommonMessage + "2.4 2.5 " + " do not support this syntax.");
          }
        }

        boolean hasProblem = false;
        StringBuilder message = new StringBuilder(myCommonMessage);

        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel languageLevel = myVersionsToProcess.get(i);
          if (languageLevel.isPy3K()) {
            PsiElement element = exceptClass.getNextSibling();
            while (element instanceof PsiWhiteSpace) {
              element = element.getNextSibling();
            }
            if (element != null && ",".equals(element.getText())) {
              if (hasProblem)
                message.append(", ");
              message.append(languageLevel.toString());
              hasProblem = true;
            }
          }
        }
        message.append(" do not this syntax.");
        if (hasProblem)
          registerProblem(node, message.toString(), new ReplaceExceptPartQuickFix());
      }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      super.visitPyImportStatement(node);
      PyImportElement[] importElements = node.getImportElements();
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        for (PyImportElement importElement : importElements) {
          final PyQualifiedName qName = importElement.getImportedQName();
          if (qName != null) {
            if (!languageLevel.isPy3K()) {
              if (qName.matches("builtins")) {
                if (hasProblem)
                  message.append(", ");
                message.append(languageLevel.toString());
                message.append(" do not have module builtins.");
                hasProblem = true;
              }
            }
            else {
              if (qName.matches("__builtin__")) {
                if (hasProblem)
                  message.append(", ");
                message.append(languageLevel.toString());
                message.append(" do not have module __builtin__.");
                hasProblem = true;
              }
            }
          }
        }
      }
      if (hasProblem)
        registerProblem(node, message.toString(), new ReplaceBuiltinsQuickFix());
    }

    @Override
    public void visitPyStarExpression(PyStarExpression node) {
      super.visitPyStarExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (!languageLevel.isPy3K()) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel.toString());
          hasProblem = true;
        }
      }
      message.append(" do not support this syntax. Starred expressions are not allowed as assignment targets in Python 2");
      if (hasProblem)
        registerProblem(node, message.toString());
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      super.visitPyBinaryExpression(node);
      boolean hasProblem = false;
      if (node.isOperator("<>")) {
        StringBuilder message = new StringBuilder(myCommonMessage);
        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel level = myVersionsToProcess.get(i);
          if (level.isPy3K()) {
            if (hasProblem)
              message.append(", ");
            message.append(level.toString());
            hasProblem = true;
          }
        }
        message.append(" do not support <>, use != instead.");
        if (hasProblem)
          registerProblem(node, message.toString(), new ReplaceNotEqOperatorQuickFix());
      }
    }

    @Override
    public void visitPyNumericLiteralExpression(final PyNumericLiteralExpression node) {
      super.visitPyNumericLiteralExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);

        if (languageLevel.isPy3K()) {
          if (!node.isIntegerLiteral()) {
            continue;
          }
          final String text = node.getText();
          if (text.endsWith("l") || text.endsWith("L")) {
            message.append(languageLevel.toString()).append(" ");
            hasProblem = true;
          }
          if (text.length() > 1 && text.charAt(0) == '0') {
            final char c = Character.toLowerCase(text.charAt(1));
            if (c != 'o' && c != 'b' && c != 'x') {
              boolean isNull = true;
              for (char a : text.toCharArray()) {
                if ( a != '0') {
                  isNull = false;
                  break;
                }
              }
              if (!isNull) {
                if (hasProblem)
                  message.append(", ");
                message.append(languageLevel.toString());
                hasProblem = true;
              }
            }
          }
        }
      }
      message.append(" do not support a trailing \'l\' or \'L\'.");
      if (hasProblem)
        registerProblem(node, message.toString(), new ReplaceOctalNumericLiteralQuickFix());
    }

    @Override
    public void visitPyStringLiteralExpression(final PyStringLiteralExpression node) {
      super.visitPyStringLiteralExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);

        if (languageLevel.isPy3K()) {
          final String text = node.getText();
          if (text.startsWith("u") || text.startsWith("U")) {
            if (hasProblem)
              message.append(", ");
            message.append(languageLevel.toString());
            hasProblem = true;
          }
        }
      }
      message.append(" do not a leading \'u\' or \'U\'.");
      if (hasProblem)
        registerProblem(node, message.toString(), new RemoveLeadingUQuickFix());
    }

    public void visitPyListCompExpression(final PyListCompExpression node) {
      super.visitPyListCompExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        boolean tmp = UnsupportedFeaturesUtil.visitPyListCompExpression(node, languageLevel);
        if (tmp) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel);
          hasProblem = true;
        }
      }
      message.append(" do not support this syntax in list comprehensions.");
      for (ComprhForComponent forComponent : node.getForComponents()) {
        final PyExpression iteratedList = forComponent.getIteratedList();
        if (hasProblem)
          registerProblem(iteratedList, message.toString(), new ReplaceListComprehensionsQuickFix());
      }
    }

    @Override
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      super.visitPyRaiseStatement(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        boolean hasNoArgs = UnsupportedFeaturesUtil.raiseHasNoArgs(node, languageLevel);
        if (hasNoArgs) {
          message.append(languageLevel).append(" ");
          hasProblem = true;
        }
      }
      message.append(" do not support this syntax. Raise with no arguments can only be used in an except block");
      if (hasProblem)
        registerProblem(node, message.toString());

      hasProblem = false;
      message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        boolean hasTwoArgs = UnsupportedFeaturesUtil.raiseHasMoreThenOneArg(node, languageLevel);
        if (hasTwoArgs) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel);
          hasProblem = true;
        }
      }
      message.append(" do not support this syntax.");
      if (hasProblem)
        registerProblem(node, message.toString(), new ReplaceRaiseStatementQuickFix());
    }

    @Override
    public void visitPyReprExpression(PyReprExpression node) {
      super.visitPyReprExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (languageLevel.isPy3K()) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel);
          hasProblem = true;
        }
      }
      message.append(" do not support backquotes, use repr() instead");
      if (hasProblem)
        registerProblem(node, message.toString(), new ReplaceBackquoteExpressionQuickFix());
    }

    @Override
    public void visitPyWithStatement(PyWithStatement node) {
      super.visitPyWithStatement(node);
      Set<PyWithItem> problemItems = new HashSet<PyWithItem>();
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (languageLevel == LanguageLevel.PYTHON24) {
          registerProblem(node, "Python version 2.4 doesn't support this syntax.");
        }
        else if (!languageLevel.supportsSetLiterals()) {
          final PyWithItem[] items = node.getWithItems();
          if (items.length > 1) {
            for (int j = 1; j < items.length; j++) {
              if (!problemItems.isEmpty())
                message.append(", ");
              message.append(languageLevel.toString());
              problemItems.add(items [j]);
            }
          }
        }
      }
      message.append(" do not support multiple context managers");
      for (PyWithItem item : problemItems) {
        registerProblem(item, message.toString());
      }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (!languageLevel.isPy3K()) {
          final PsiElement firstChild = node.getFirstChild();
          if (firstChild != null) {
            final String name = firstChild.getText();
            if (PyNames.SUPER.equals(name)) {
              final PyArgumentList argumentList = node.getArgumentList();
              if (argumentList != null && argumentList.getArguments().length == 0) {
                if (hasProblem)
                  message.append(", ");
                message.append(languageLevel.toString());
                hasProblem = true;
              }
            }
          }
        }
      }
      if (hasProblem)
        registerProblem(node, message.toString());

      hasProblem = false;
      message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);

        PyExpression callee = node.getCallee();
        assert callee != null;
        PsiReference reference = callee.getReference();
        if (reference != null) {
          PsiElement resolved = reference.resolve();
          ProjectFileIndex ind = ProjectRootManager.getInstance(callee.getProject()).getFileIndex();
          if (resolved != null) {
            PsiFile file = resolved.getContainingFile();
            if (file != null && ind.isInLibraryClasses(file.getVirtualFile())) {
              final String name = callee.getText();
              if (!name.equals("print") && UnsupportedFeaturesUtil.BUILTINS.get(languageLevel).contains(name)) {
                if (hasProblem)
                  message.append(", ");
                hasProblem = true;
                message.append(languageLevel.toString());
              }
            }
          }
        }
      }
      if (hasProblem) {
        message.append(" have no method ").append(node.getCallee().getText());
        registerProblem(node, message.toString());
      }
    }

    @Override
    public void visitPyClass(PyClass node) {    //PY-2719
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
        PyArgumentList list = node.getSuperClassExpressionList();
        if (list != null && list.getArguments().length == 0)
          registerProblem(list, "Python version 2.4 does not support this syntax.");
      }
    }

    @Override
    public void visitPyPrintStatement(PyPrintStatement node) {
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON30) || myVersionsToProcess.contains(LanguageLevel.PYTHON31)) {
        PsiElement[] arguments = node.getChildren();
        for (PsiElement element : arguments) {
          if (!((element instanceof PyParenthesizedExpression) || (element instanceof PyTupleExpression)))
            registerProblem(element, "Python versions >= 3.0 do not support this syntax. The print statement has been replaced with a print() function");
        }
      }
    }

    @Override
    public void visitPyImportElement(PyImportElement node) {
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      for (int i = 0; i != myVersionsToProcess.size(); ++i) {
        LanguageLevel languageLevel = myVersionsToProcess.get(i);
        if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(node.getText())) {
          if (hasProblem)
            message.append(", ");
          message.append(languageLevel.toString());
          hasProblem = true;
        }
      }
      message.append(" do not have module ").append(node.getText());
      if (hasProblem)
        registerProblem(node, message.toString());
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      boolean hasProblem = false;
      StringBuilder message = new StringBuilder(myCommonMessage);
      PyReferenceExpression importSource  = node.getImportSource();
      if (importSource != null) {
        if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {      //PY-2793
          PsiElement prev = importSource.getPrevSibling();
          if (prev != null && prev.getNode().getElementType() == PyTokenTypes.DOT)
            registerProblem(node, "Python version 2.4 doesn't support this syntax.");
        }

        String name = importSource.getText();
        for (int i = 0; i != myVersionsToProcess.size(); ++i) {
          LanguageLevel languageLevel = myVersionsToProcess.get(i);
          if (UnsupportedFeaturesUtil.MODULES.get(languageLevel).contains(name)) {
            if (hasProblem)
              message.append(", ");
            message.append(languageLevel.toString());
            hasProblem = true;
          }
        }
        message.append(" do not have module ").append(name);
        if (hasProblem)
          registerProblem(node, message.toString());
      }
      else {
        if (myVersionsToProcess.contains(LanguageLevel.PYTHON24))
          registerProblem(node, "Python version 2.4 doesn't support this syntax.");
      }
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
        PyExpression assignedValue = node.getAssignedValue();
        if (assignedValue instanceof PyConditionalExpression)                        // PY-2792
          registerProblem(node, "Python version 2.4 doesn't support this syntax.");

        Stack<PsiElement> st = new Stack<PsiElement>();           // PY-2796
        st.push(assignedValue);
        while (!st.isEmpty()) {
          PsiElement el = st.pop();
          if (el instanceof PyYieldExpression)
            registerProblem(node, "Python version 2.4 doesn't support this syntax. " +
                                                      "In Python <= 2.4, yield was a statement; it didn’t return any value.");
          else {
            for (PsiElement e : el.getChildren())
              st.push(e);
          }
        }
      }
    }

    @Override
    public void visitPyTryExceptStatement(PyTryExceptStatement node) { // PY-2795
      if (myVersionsToProcess.contains(LanguageLevel.PYTHON24)) {
        PyExceptPart[] excepts =  node.getExceptParts();
        PyFinallyPart finallyPart = node.getFinallyPart();
        if (excepts.length != 0 && finallyPart != null)
          registerProblem(node, "Python version 2.4 doesn't support this syntax. You could use a finally block to ensure " +
                                                  "that code is always executed, or one or more except blocks to catch specific exceptions.");
      }
    }
  }
}
