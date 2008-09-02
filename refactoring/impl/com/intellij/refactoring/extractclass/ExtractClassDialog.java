package com.intellij.refactoring.extractclass;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.*;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.PackageNameUtil;
import com.intellij.refactoring.ui.MemberSelectionPanel;
import com.intellij.refactoring.ui.MemberSelectionTable;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.refactoring.util.classMembers.DelegatingMemberInfoModel;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoChange;
import com.intellij.refactoring.util.classMembers.MemberInfoChangeListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class ExtractClassDialog extends RefactoringDialog implements MemberInfoChangeListener {
  private Map<MemberInfo, PsiMember> myMember2CauseMap = new HashMap<MemberInfo, PsiMember>();
  private final PsiClass sourceClass;
  private final MemberInfo[] memberInfo;
  private final JTextField classNameField;
  private final JTextField packageTextField;
  private final FixedSizeButton packageChooserButton;
  private final JTextField sourceClassTextField;

  ExtractClassDialog(PsiClass sourceClass, PsiMember selectedMember) {
    super(sourceClass.getProject(), true);
    setModal(true);
    setTitle(RefactorJBundle.message("extract.class.title"));
    this.sourceClass = sourceClass;
    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };
    classNameField = new JTextField();
    packageTextField = new JTextField();
    classNameField.getDocument().addDocumentListener(docListener);
    packageTextField.getDocument().addDocumentListener(docListener);
    packageChooserButton = new FixedSizeButton(packageTextField);
    sourceClassTextField = new JTextField();
    final MemberInfo.Filter filter = new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        if (element instanceof PsiMethod) {
          return !((PsiMethod)element).isConstructor();
        }
        else if (element instanceof PsiField) {
          //don't include static fields with non-constant initializers
          final PsiField field = (PsiField)element;
          if (!field.hasModifierProperty(PsiModifier.STATIC)) {
            return true;
          }
          if (!field.hasInitializer()) {
            return true;
          }
          final PsiExpression initializer = field.getInitializer();
          return PsiUtil.isConstantExpression(initializer);
        }
        else if (element instanceof PsiClass) {
          return true;
        }
        return false;
      }
    };
    memberInfo = MemberInfo.extractClassMembers(this.sourceClass, filter, true);
    for (MemberInfo info : memberInfo) {
      if (info.getMember().equals(selectedMember)) {
        info.setChecked(true);
      }
    }
    super.init();

    final PsiFile file = sourceClass.getContainingFile();
    if (file instanceof PsiJavaFile) {
      packageTextField.setText(((PsiJavaFile)file).getPackageName());
    }
    sourceClassTextField.setText(sourceClass.getQualifiedName());
    validateButtons();
  }

  protected void doAction() {

    final List<PsiField> fields = getFieldsToExtract();
    final List<PsiMethod> methods = getMethodsToExtract();
    final List<PsiClass> classes = getClassesToExtract();
    final String newClassName = getClassName();
    final String packageName = getPackageName();

    final ExtractClassProcessor processor = new ExtractClassProcessor(sourceClass, fields, methods, classes, packageName, newClassName);
    invokeRefactoring(processor);
  }

  protected boolean areButtonsValid() {
    final Project project = sourceClass.getProject();
    final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
    final List<PsiMethod> methods = getMethodsToExtract();
    final List<PsiField> fields = getFieldsToExtract();
    final List<PsiClass> innerClasses = getClassesToExtract();
    if (methods.isEmpty() && fields.isEmpty() && innerClasses.isEmpty()) {
      return false;
    }

    final String className = getClassName();
    if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
      return false;
    }

    final String packageName = getPackageName();
    return !(packageName.length() == 0 || PackageNameUtil.containsNonIdentifier(nameHelper, packageName));
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  public List<PsiField> getFieldsToExtract() {
    return getFieldsToExtract(true);
  }

  public List<PsiField> getFieldsToExtract(final boolean checked) {
    final List<PsiField> out = new ArrayList<PsiField>();
    for (MemberInfo info : memberInfo) {
      if (checked && !info.isChecked()) continue;
      if (!checked && info.isChecked()) continue;
      final PsiMember member = info.getMember();
      if (member instanceof PsiField) {
        out.add((PsiField)member);
      }
    }
    return out;
  }

  public List<PsiMethod> getMethodsToExtract() {
    return getMethodsToExtract(true);
  }

  public List<PsiMethod> getMethodsToExtract(final boolean checked) {
    final List<PsiMethod> out = new ArrayList<PsiMethod>();
    for (MemberInfo info : memberInfo) {
      if (checked && !info.isChecked()) continue;
      if (!checked && info.isChecked()) continue;
      final PsiMember member = info.getMember();
      if (member instanceof PsiMethod) {
        out.add((PsiMethod)member);
      }
    }
    return out;
  }

  public List<PsiClass> getClassesToExtract() {
    return getClassesToExtract(true);
  }

  public List<PsiClass> getClassesToExtract(final boolean checked) {
    final List<PsiClass> out = new ArrayList<PsiClass>();
    for (MemberInfo info : memberInfo) {
      if (checked && !info.isChecked()) continue;
      if (!checked && info.isChecked()) continue;
      final PsiMember member = info.getMember();
      if (member instanceof PsiClass) {
        out.add((PsiClass)member);
      }
    }
    return out;
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.ExtractClass";
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createVerticalBox();

    sourceClassTextField.setEditable(false);
    final JPanel sourceClassPanel = new JPanel(new BorderLayout());
    sourceClassPanel.add(new JLabel(RefactorJBundle.message("extract.class.from.label")), BorderLayout.NORTH);
    sourceClassPanel.add(sourceClassTextField, BorderLayout.CENTER);
    box.add(sourceClassPanel);

    box.add(Box.createVerticalStrut(10));
    final JLabel classNameLabel = new JLabel(RefactorJBundle.message("name.for.new.class.label"));
    classNameLabel.setLabelFor(classNameField);
    classNameLabel.setDisplayedMnemonic('N');
    final JPanel classNamePanel = new JPanel(new BorderLayout());
    classNamePanel.add(classNameLabel, BorderLayout.NORTH);
    classNamePanel.add(classNameField, BorderLayout.CENTER);
    box.add(classNamePanel);

    box.add(Box.createVerticalStrut(5));
    packageChooserButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = sourceClass.getProject();
        final PackageChooserDialog chooser = new PackageChooserDialog(RefactorJBundle.message("choose.destination.package.label"), project);
        chooser.selectPackage(packageTextField.getText());
        chooser.show();
        final PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          packageTextField.setText(aPackage.getQualifiedName());
        }
      }
    });
    final JPanel packageNamePanel = new JPanel(new BorderLayout());
    final JLabel packageLabel = new JLabel(RefactorJBundle.message("package.for.new.class.label"));
    packageLabel.setLabelFor(packageTextField);
    packageLabel.setDisplayedMnemonic('P');
    packageNamePanel.add(packageLabel, BorderLayout.NORTH);
    packageNamePanel.add(packageTextField, BorderLayout.CENTER);
    packageNamePanel.add(packageChooserButton, BorderLayout.EAST);
    box.add(packageNamePanel);

    box.add(Box.createVerticalStrut(10));
    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final MemberSelectionPanel memberSelectionPanel =
      new MemberSelectionPanel(RefactorJBundle.message("members.to.extract.label"), memberInfo, null);
    final MemberSelectionTable table = memberSelectionPanel.getTable();
    table.setMemberInfoModel(new DelegatingMemberInfoModel(table.getMemberInfoModel()) {
      public Boolean isFixedAbstract(MemberInfo member) {
        return Boolean.TRUE;
      }

      @Override
      public int checkForProblems(@NotNull final MemberInfo member) {
        final PsiMember cause = getCause(member);
        if (member.isChecked() && cause != null) return ERROR;
        if (!member.isChecked() && cause != null) return WARNING;
        return OK;
      }

      @Override
      public String getTooltipText(final MemberInfo member) {
        final PsiMember cause = getCause(member);
        if (cause != null) {
          final String presentation = SymbolPresentationUtil.getSymbolPresentableText(cause);
          if (member.isChecked()) {
            return "Depends on " + presentation + " from " + sourceClass.getName();
          } else {
            final String className = getClassName();
            return "Depends on " + presentation + " from new class" + (className.length() > 0 ? ": " + className : "");
          }
        }
        return null;
      }

      private PsiMember getCause(final MemberInfo member) {
        PsiMember cause = myMember2CauseMap.get(member);

        if (cause != null) return cause;

        final BackpointerUsageVisitor visitor;
        if (member.isChecked()) {
          visitor = new BackpointerUsageVisitor(getFieldsToExtract(), getClassesToExtract(), getMethodsToExtract(), sourceClass);
        }
        else {
          visitor =
            new BackpointerUsageVisitor(getFieldsToExtract(false), getClassesToExtract(false), getMethodsToExtract(false), sourceClass, false);
        }

        member.getMember().accept(visitor);
        cause = visitor.getCause();
        myMember2CauseMap.put(member, cause);
        return cause;
      }
    });
    panel.add(memberSelectionPanel, BorderLayout.CENTER);
    table.addMemberInfoChangeListener(this);
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return classNameField;
  }

  protected void doHelpAction() {
    final HelpManager helpManager = HelpManager.getInstance();
    helpManager.invokeHelp(HelpID.ExtractClass);
  }

  public void memberInfoChanged(MemberInfoChange memberInfoChange) {
    validateButtons();
    myMember2CauseMap.clear();
  }
}