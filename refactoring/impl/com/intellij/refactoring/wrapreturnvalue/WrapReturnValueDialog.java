package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.ide.util.PackageChooserDialog;
import com.intellij.ide.util.TreeClassChooserDialog;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.RefactorJHelpID;
import com.intellij.refactoring.base.BaseRefactoringDialog;
import com.intellij.refactoring.psi.PackageNameUtil;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

@SuppressWarnings({"OverridableMethodCallInConstructor"})
class WrapReturnValueDialog extends BaseRefactoringDialog{

    private final PsiMethod sourceMethod;
    private final JTextField existingClassField;
    private final FixedSizeButton existingClassChooserButton;
    private final JTextField classNameField;
    private final JTextField packageTextField;
    private final FixedSizeButton packageChooserButton;
    private final JTextField sourceMethodTextField;
    private final JRadioButton useExistingClassButton = new JRadioButton("Use existing class");
    private final JRadioButton createNewClassButton = new JRadioButton("Create new class");
    private JLabel classNameLabel;
    private JLabel packageLabel;

    WrapReturnValueDialog(PsiMethod sourceMethod){
        super(sourceMethod.getProject(), true);
        setModal(true);
        setTitle(RefactorJBundle.message("wrap.return.value.title"));
        this.sourceMethod = sourceMethod;
        final DocumentListener docListener = new ValidationDocListener();
        existingClassField = new JTextField();
        final Document existingClassFieldDocument = existingClassField.getDocument();
        existingClassFieldDocument.addDocumentListener(docListener);
        existingClassChooserButton = new FixedSizeButton(existingClassField);
        packageTextField = new JTextField();
        final Document packageFieldDocument = packageTextField.getDocument();
        packageFieldDocument.addDocumentListener(docListener);
        packageChooserButton = new FixedSizeButton(packageTextField);
        classNameField = new JTextField();
        final Document classFieldDocument = classNameField.getDocument();
        classFieldDocument.addDocumentListener(docListener);
        sourceMethodTextField = new JTextField();
        classNameLabel = new JLabel(RefactorJBundle.message("name.for.wrapper.class.label"));
        packageLabel = new JLabel(RefactorJBundle.message("package.for.wrapper.class.label"));

        final PsiFile file = sourceMethod.getContainingFile();
        if(file instanceof PsiJavaFile){
            final String packageName = ((PsiJavaFile) file).getPackageName();
            packageTextField.setText(packageName);
        }
        final PsiClass containingClass = sourceMethod.getContainingClass();
        final String containingClassName = containingClass.getName();
        final String sourceMethodName = sourceMethod.getName();
        sourceMethodTextField.setText(containingClassName + '.' + sourceMethodName);
        final ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(useExistingClassButton);
        buttonGroup.add(createNewClassButton);
        useExistingClassButton.setSelected(true);
        init();
        final ActionListener listener = new ActionListener(){

            public void actionPerformed(ActionEvent actionEvent){
                toggleRadioEnablement();
            }
        };
        useExistingClassButton.addActionListener(listener);
        createNewClassButton.addActionListener(listener);
        toggleRadioEnablement();
        validateButtons();
    }

    private void toggleRadioEnablement(){
        final boolean useExisting = useExistingClassButton.isSelected();
        existingClassField.setEnabled(useExisting);
        existingClassChooserButton.setEnabled(useExisting);
        classNameField.setEnabled(!useExisting);
        classNameLabel.setEnabled(!useExisting);
        packageLabel.setEnabled(!useExisting);
        packageTextField.setEnabled(!useExisting);
        packageChooserButton.setEnabled(!useExisting);
    }

    protected String getDimensionServiceKey(){
        return "RefactorJ.WrapReturnValue";
    }

    protected boolean isValid(){
        if(useExistingClass()){
            return existingClassField.getText().length() != 0;
        }
        final Project project = sourceMethod.getProject();
        final PsiNameHelper nameHelper = JavaPsiFacade.getInstance(project).getNameHelper();
        final String packageName = getPackageName();
        if(packageName.length() == 0
                || PackageNameUtil.containsNonIdentifier(nameHelper, packageName)){
            return false;
        }
        final String className = getClassName();
        return !(className.length() == 0
                || !nameHelper.isIdentifier(className));
    }

    @NotNull
    public String getPackageName(){
      return packageTextField.getText().trim();
    }

    @NotNull
    public String getClassName(){
      return classNameField.getText().trim();
    }

    protected JComponent createCenterPanel(){
        final Box box = Box.createVerticalBox();

        sourceMethodTextField.setEditable(false);
        final JPanel methodNamePanel = new JPanel(new BorderLayout());
        methodNamePanel
                .add(new JLabel(RefactorJBundle.message("method.to.wrap.returns.from.label")), BorderLayout.NORTH);
        methodNamePanel.add(sourceMethodTextField, BorderLayout.CENTER);
        box.add(methodNamePanel);

        createVerticalStrut(box, 10);
        final JPanel existingClassPanel = new JPanel(new BorderLayout());
        final JLabel existingClassLabel = new JLabel();
        existingClassLabel.setLabelFor(classNameField);
        existingClassLabel.setDisplayedMnemonic('N');
        existingClassPanel.add(existingClassLabel, BorderLayout.WEST);
        existingClassPanel.add(existingClassField, BorderLayout.CENTER);
        existingClassPanel.add(existingClassChooserButton, BorderLayout.EAST);

        existingClassChooserButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                final Project project = sourceMethod.getProject();
                final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
                final TreeClassChooserDialog chooser = new TreeClassChooserDialog(
                        RefactorJBundle.message("select.wrapper.class"), project, scope, null, null);
                final String classText = existingClassField.getText();
              final PsiClass currentClass = JavaPsiFacade.getInstance(PsiManager.getInstance(project).getProject()).findClass(classText, GlobalSearchScope.allScope(project))
                ;
                if(currentClass != null){
                    chooser.selectClass(currentClass);
                }
                chooser.show();
                final PsiClass selectedClass = chooser.getSelectedClass();
                if(selectedClass != null){
                    final String className = selectedClass.getQualifiedName();
                    existingClassField.setText(className);
                }
            }
        });

        createVerticalStrut(box, 10);
        final JPanel classNamePanel = new JPanel(new GridBagLayout());
        final TitledBorder newClassBorder = IdeBorderFactory.createTitledBorder(
                "Wrapper Class");
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

        packageChooserButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e){
                final Project project = sourceMethod.getProject();
                final PackageChooserDialog chooser = new PackageChooserDialog(
                        RefactorJBundle.message("choose.destination.package.label"), project);
                final String packageText = packageTextField.getText();
                chooser.selectPackage(packageText);
                chooser.show();
                final PsiPackage aPackage = chooser.getSelectedPackage();
                if(aPackage != null){
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
        createVerticalStrut(box, 10);

        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(box, BorderLayout.CENTER);
        return panel;
    }

    private static void createVerticalStrut(Box box, int strutSize){
        final Component strut = Box.createVerticalStrut(strutSize);
        box.add(strut);
    }

    public JComponent getPreferredFocusedComponent(){
        return classNameField;
    }

    protected void doHelpAction(){
        final HelpManager helpManager = HelpManager.getInstance();
        helpManager.invokeHelp(RefactorJHelpID.WrapReturnValue);
    }

    public String getExistingClassName(){
      return existingClassField.getText().trim();
    }

    public boolean useExistingClass(){
        return useExistingClassButton.isSelected();
    }
}