package com.intellij.refactoring.introduceparameterobject.ui;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.introduceparameterobject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.psi.PackageNameUtil;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
public class IntroduceParameterObjectDialog extends RefactoringDialog implements ParameterInfoChangeListener {

  private final PsiMethod sourceMethod;
  private final ParameterInfo[] parameterInfo;
  private final JTextField classNameField;
  private final JTextField packageTextField;
  private final FixedSizeButton packageChooserButton;
  private final JTextField sourceMethodTextField;
  private final JTextField existingClassField;
  private final FixedSizeButton existingClassChooserButton;
  private final JRadioButton useExistingClassButton = new JRadioButton("Use existing class");
  private final JRadioButton createNewClassButton = new JRadioButton("Create new class");
  private final JCheckBox keepMethodAsDelegate = new JCheckBox("Keep method as delegate");
  private JLabel classNameLabel;
  private JLabel packageLabel;


  public IntroduceParameterObjectDialog(PsiMethod sourceMethod) {
    super(sourceMethod.getProject(), true);
    this.sourceMethod = sourceMethod;
    setTitle(RefactorJBundle.message("introduce.parameter.object.title"));
    final DocumentListener docListener = new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        validateButtons();
      }
    };
    packageTextField = new JTextField();
    packageTextField.getDocument().addDocumentListener(docListener);
    packageChooserButton = new FixedSizeButton(packageTextField);
    existingClassField = new JTextField();
    existingClassField.getDocument().addDocumentListener(docListener);
    existingClassChooserButton = new FixedSizeButton(existingClassField);
    classNameField = new JTextField();
    classNameField.getDocument().addDocumentListener(docListener);
    sourceMethodTextField = new JTextField();
    parameterInfo = ParameterInfo.fromMethod(sourceMethod);
    classNameLabel = new JLabel(RefactorJBundle.message("name.for.wrapper.class.label"));
    packageLabel = new JLabel(RefactorJBundle.message("package.for.wrapper.class.label"));

    final PsiFile file = sourceMethod.getContainingFile();
    if (file instanceof PsiJavaFile) {
      packageTextField.setText(((PsiJavaFile)file).getPackageName());
    }
    final PsiClass containingClass = sourceMethod.getContainingClass();
    sourceMethodTextField.setText(containingClass.getName() + '.' + sourceMethod.getName());
    final ButtonGroup buttonGroup = new ButtonGroup();
    buttonGroup.add(useExistingClassButton);
    buttonGroup.add(createNewClassButton);
    useExistingClassButton.setSelected(true);
    init();
    final ActionListener listener = new ActionListener() {

      public void actionPerformed(ActionEvent actionEvent) {
        toggleRadioEnablement();
      }
    };
    useExistingClassButton.addActionListener(listener);
    createNewClassButton.addActionListener(listener);
    toggleRadioEnablement();
    validateButtons();
  }

  private void toggleRadioEnablement() {
    final boolean useExisting = useExistingClassButton.isSelected();
    existingClassField.setEnabled(useExisting);
    existingClassChooserButton.setEnabled(useExisting);
    classNameField.setEnabled(!useExisting);
    classNameLabel.setEnabled(!useExisting);
    packageLabel.setEnabled(!useExisting);
    packageTextField.setEnabled(!useExisting);
    packageChooserButton.setEnabled(!useExisting);
  }

  protected String getDimensionServiceKey() {
    return "RefactorJ.IntroduceParameterObject";
  }

  protected void doAction() {
    final List<PsiParameter> params = getParametersToExtract();
    final boolean useExistingClass = useExistingClass();
    final boolean keepMethod = keepMethodAsDelegate();
    final IntroduceParameterObjectProcessor processor;
    if (!useExistingClass) {
      processor =
        new IntroduceParameterObjectProcessor(getClassName(), getPackageName(), sourceMethod, params, null, keepMethod, isPreviewUsages(),
                                              useExistingClass);
    }
    else {

      final String existingClassName = getExistingClassName();
      final List<String> getterNames = new ArrayList<String>();


      final int index = existingClassName.lastIndexOf('.');
      final String packageName = index == -1 ? "" : existingClassName.substring(0, index);

      processor = new IntroduceParameterObjectProcessor(index == -1 ? existingClassName : existingClassName.substring(index + 1), packageName,
                                                        sourceMethod, params, getterNames, keepMethod, isPreviewUsages(), useExistingClass);
    }
    invokeRefactoring(processor);
  }

  @Override
  protected boolean areButtonsValid() {
    final Project project = sourceMethod.getProject();
    final JavaPsiFacade manager = JavaPsiFacade.getInstance(project);
    final PsiNameHelper nameHelper = manager.getNameHelper();


    final List<PsiParameter> parametersToExtract = getParametersToExtract();
    if (parametersToExtract.isEmpty()) {
      return false;
    }
    if (!useExistingClass()) {
      final String className = getClassName();
      if (className.length() == 0 || !nameHelper.isIdentifier(className)) {
        return false;
      }
      final String packageName = getPackageName();

      if (packageName.length() == 0 || PackageNameUtil.containsNonIdentifier(nameHelper, packageName)) {
        return false;
      }
      else {
        return true;
      }
    }
    else {
      return getExistingClassName().length() != 0;
    }
  }

  @NotNull
  public String getPackageName() {
    return packageTextField.getText().trim();
  }

  @NotNull
  public String getExistingClassName() {
    return existingClassField.getText().trim();
  }

  @NotNull
  public String getClassName() {
    return classNameField.getText().trim();
  }

  @NotNull
  public List<PsiParameter> getParametersToExtract() {
    final List<PsiParameter> out = new ArrayList<PsiParameter>();
    for (ParameterInfo info : parameterInfo) {
      if (info.isChecked()) {
        out.add(info.getParameter());
      }
    }
    return out;
  }

  protected JComponent createNorthPanel() {
    final Box box = Box.createVerticalBox();

    sourceMethodTextField.setEditable(false);
    final JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(new JLabel(RefactorJBundle.message("method.to.extract.parameters.from.label")), BorderLayout.NORTH);
    _panel.add(sourceMethodTextField, BorderLayout.CENTER);
    box.add(_panel);

    box.add(Box.createVerticalStrut(10));

    final JPanel existingClassPanel = new JPanel(new BorderLayout());
    final JLabel existingClassLabel = new JLabel();
    existingClassLabel.setLabelFor(classNameField);
    existingClassLabel.setDisplayedMnemonic('N');
    existingClassPanel.add(existingClassLabel, BorderLayout.WEST);
    existingClassPanel.add(existingClassField, BorderLayout.CENTER);
    existingClassPanel.add(existingClassChooserButton, BorderLayout.EAST);

    existingClassChooserButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = sourceMethod.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final TreeClassChooserDialog chooser =
          new TreeClassChooserDialog(RefactorJBundle.message("select.wrapper.class"), project, scope, null, null);
        final String classText = existingClassField.getText();
        final PsiClass currentClass = JavaPsiFacade.getInstance(project).findClass(classText, GlobalSearchScope.allScope(project));
        if (currentClass != null) {
          chooser.selectClass(currentClass);
        }
        chooser.show();
        final PsiClass selectedClass = chooser.getSelectedClass();
        if (selectedClass != null) {
          final String className = selectedClass.getQualifiedName();
          existingClassField.setText(className);
        }
      }
    });

    box.add(Box.createVerticalStrut(10));
    final JPanel classNamePanel = new JPanel(new GridBagLayout());
    final TitledBorder newClassBorder = IdeBorderFactory.createTitledBorder("Wrapper Class");
    classNamePanel.setBorder(newClassBorder);
    final Insets indent = new Insets(0, 2, 0, 0);
    final GridBagConstraints gbConstraints = new GridBagConstraints();
    final Insets origInsets = gbConstraints.insets;
    gbConstraints.fill = GridBagConstraints.HORIZONTAL;
    gbConstraints.weightx = 1.0;
    gbConstraints.weighty = 0.0;
    gbConstraints.gridwidth = 3;
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 0;
    gbConstraints.gridwidth = 3;
    useExistingClassButton.setMnemonic('U');
    classNamePanel.add(useExistingClassButton, gbConstraints);
    gbConstraints.gridy = 1;
    gbConstraints.insets = indent;
    classNamePanel.add(existingClassPanel, gbConstraints);
    gbConstraints.insets = origInsets;
    gbConstraints.gridy = 2;
    createNewClassButton.setMnemonic('C');
    classNamePanel.add(createNewClassButton, gbConstraints);
    gbConstraints.gridy = 3;
    gbConstraints.gridwidth = 0;

    classNameLabel.setLabelFor(classNameField);
    classNameLabel.setDisplayedMnemonic('N');
    gbConstraints.insets = indent;
    classNamePanel.add(classNameLabel, gbConstraints);
    gbConstraints.insets = origInsets;
    gbConstraints.gridx = 1;
    gbConstraints.gridwidth = 2;
    classNamePanel.add(classNameField, gbConstraints);

    packageChooserButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final Project project = sourceMethod.getProject();
        final PackageChooserDialog chooser = new PackageChooserDialog(RefactorJBundle.message("choose.destination.package.label"), project);
        final String packageText = packageTextField.getText();
        chooser.selectPackage(packageText);
        chooser.show();
        final PsiPackage aPackage = chooser.getSelectedPackage();
        if (aPackage != null) {
          final String packageName = aPackage.getQualifiedName();
          packageTextField.setText(packageName);
        }
      }
    });
    packageLabel.setLabelFor(packageTextField);
    packageLabel.setDisplayedMnemonic('P');
    gbConstraints.gridx = 0;
    gbConstraints.gridy = 4;
    gbConstraints.gridwidth = 1;
    gbConstraints.insets = indent;
    classNamePanel.add(packageLabel, gbConstraints);
    gbConstraints.insets = origInsets;
    gbConstraints.gridx = 1;
    classNamePanel.add(packageTextField, gbConstraints);
    gbConstraints.gridx = 2;
    gbConstraints.fill = GridBagConstraints.NONE;
    classNamePanel.add(packageChooserButton, gbConstraints);
    box.add(classNamePanel);

    box.add(Box.createVerticalStrut(10));

    final JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    return panel;
  }

  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    final ParameterSelectionPanel parameterSelectionPanel = new ParameterSelectionPanel(parameterInfo);
    final ParameterSelectionTable table = parameterSelectionPanel.getTable();
    table.addParameterInfoChangeListener(this);
    panel.add(parameterSelectionPanel, BorderLayout.CENTER);
    panel.add(keepMethodAsDelegate, BorderLayout.SOUTH);
    return panel;
  }

  public JComponent getPreferredFocusedComponent() {
    return existingClassField;
  }

  protected void doHelpAction() {
    final HelpManager helpManager = HelpManager.getInstance();
    helpManager.invokeHelp(RefactorJHelpID.IntroduceParameterObject);
  }

  public void parameterInfoChanged() {
    validateButtons();
  }

  public boolean useExistingClass() {
    return useExistingClassButton.isSelected();
  }

  public boolean keepMethodAsDelegate() {
    return keepMethodAsDelegate.isSelected();
  }
}