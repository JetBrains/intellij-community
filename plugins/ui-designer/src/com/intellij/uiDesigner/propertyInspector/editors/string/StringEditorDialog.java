// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.CommonBundle;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.ide.util.TreeFileChooserFactory;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.PropertiesUtilBase;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.binding.FormReferenceProvider;
import com.intellij.uiDesigner.compiler.AsmCodeGenerator;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SlowOperations;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

public final class StringEditorDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance(StringEditorDialog.class);

  private static final @NonNls String CARD_STRING = "string";
  private static final @NonNls String CARD_BUNDLE = "bundle";

  private final GuiEditor myEditor;
  /** Descriptor to be edited */
  private StringDescriptor myValue;
  private final MyForm myForm;
  private final Locale myLocale;
  private boolean myDefaultBundleInitialized = false;

  StringEditorDialog(final Component parent,
                     final StringDescriptor descriptor,
                     @Nullable Locale locale,
                     final GuiEditor editor) {
    super(parent, true);
    myLocale = locale;

    myEditor = editor;

    myForm = new MyForm();
    setTitle(UIDesignerBundle.message("title.edit.text"));
    setValue(descriptor);

    init(); /* run initialization proc */
  }

  @Override
  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if(myForm.myRbString.isSelected()){
      return myForm.myTfValue;
    }
    else{
      return super.getPreferredFocusedComponent();
    }
  }

  @Override protected void doOKAction() {
    if (myForm.myRbResourceBundle.isSelected()) {
      final StringDescriptor descriptor = getDescriptor();
      if (descriptor != null && !descriptor.getKey().isEmpty()) {
        final String value = myForm.myTfRbValue.getText();
        final PropertiesFile propFile = getPropertiesFile(descriptor);
        try (AccessToken ignore = SlowOperations.knownIssue("IDEA-307701, EA-687662")) {
          if (propFile != null && propFile.findPropertyByKey(descriptor.getKey()) == null) {
            saveCreatedProperty(propFile, descriptor.getKey(), value, myEditor.getPsiFile());
          }
          else {
            final String newKeyName = saveModifiedPropertyValue(myEditor.getModule(), descriptor, myLocale, value, myEditor.getPsiFile());
            if (newKeyName != null) {
              myForm.myTfKey.setText(newKeyName);
            }
          }
        }
      }
    }
    super.doOKAction();
  }

  private PropertiesFile getPropertiesFile(final StringDescriptor descriptor) {
    final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(myEditor.getProject());
    return manager.findPropertiesFile(myEditor.getModule(), descriptor.getDottedBundleName(), myLocale);
  }

  public static @Nullable String saveModifiedPropertyValue(final Module module, final StringDescriptor descriptor,
                                                           final Locale locale, final String editedValue, final PsiFile formFile) {
    final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
    final PropertiesFile propFile = manager.findPropertiesFile(module, descriptor.getDottedBundleName(), locale);
    if (propFile != null) {
      final IProperty propertyByKey = propFile.findPropertyByKey(descriptor.getKey());
      if (propertyByKey instanceof Property && !editedValue.equals(propertyByKey.getValue())) {
        final Collection<PsiReference> references = findPropertyReferences((Property)propertyByKey, module);

        String newKeyName = null;
        if (references.size() > 1) {
          final int rc = Messages.showYesNoCancelDialog(module.getProject(), UIDesignerBundle.message("edit.text.multiple.usages",
                                                                                           propertyByKey.getUnescapedKey(), references.size()),
                                             UIDesignerBundle.message("edit.text.multiple.usages.title"),
                                               UIDesignerBundle.message("edit.text.change.all"),
                                               UIDesignerBundle.message("edit.text.make.unique"),
                                               CommonBundle.getCancelButtonText(),
                                             Messages.getWarningIcon());
          if (rc == Messages.CANCEL) {
            return null;
          }
          if (rc == Messages.NO) {
            newKeyName = promptNewKeyName(module.getProject(), propFile, descriptor.getKey());
            if (newKeyName == null) return null;
          }
        }
        final ReadonlyStatusHandler.OperationStatus operationStatus =
          ReadonlyStatusHandler.getInstance(module.getProject()).ensureFilesWritable(Collections.singletonList(propFile.getVirtualFile()));
        if (operationStatus.hasReadonlyFiles()) {
          return null;
        }
        final String newKeyName1 = newKeyName;
        CommandProcessor.getInstance().executeCommand(
          module.getProject(),
          () -> {
            UndoUtil.markPsiFileForUndo(formFile);
            ApplicationManager.getApplication().runWriteAction(() -> {
              PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();
              try {
                if (newKeyName1 != null) {
                  propFile.addProperty(newKeyName1, editedValue);
                }
                else {
                  final IProperty propertyByKey1 = propFile.findPropertyByKey(descriptor.getKey());
                  if (propertyByKey1 != null) {
                    propertyByKey1.setValue(editedValue);
                  }
                }
              }
              catch (IncorrectOperationException e) {
                LOG.error(e);
              }
            });
          }, UIDesignerBundle.message("command.update.property"), null);
        return newKeyName;
      }
    }
    return null;
  }

  private static Collection<PsiReference> findPropertyReferences(final Property property, final Module module) {
    final Collection<PsiReference> references = Collections.synchronizedList(new ArrayList<>());
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
      (Runnable)() -> ReferencesSearch.search(property).forEach(psiReference -> {
        PsiMethod method = PsiTreeUtil.getParentOfType(psiReference.getElement(), PsiMethod.class);
        if (method == null || !AsmCodeGenerator.SETUP_METHOD_NAME.equals(method.getName())) {
          references.add(psiReference);
        }
        return true;
      }), UIDesignerBundle.message("edit.text.searching.references"), false, module.getProject()
    );
    return references;
  }

  private static String promptNewKeyName(final Project project, final PropertiesFile propFile, final String key) {
    String newName;
    int index = 0;
    do {
      index++;
      newName = key + index;
    } while(propFile.findPropertyByKey(newName) != null);

    InputValidator validator = new InputValidator() {
      @Override
      public boolean checkInput(String inputString) {
        return !inputString.isEmpty() && propFile.findPropertyByKey(inputString) == null;
      }

      @Override
      public boolean canClose(String inputString) {
        return checkInput(inputString);
      }
    };
    return Messages.showInputDialog(project, UIDesignerBundle.message("edit.text.unique.key.prompt"),
                                    UIDesignerBundle.message("edit.text.multiple.usages.title"),
                                    Messages.getQuestionIcon(), newName, validator);
  }

  public static boolean saveCreatedProperty(final PropertiesFile bundle, final String name, final String value,
                                            final PsiFile formFile) {
    final ReadonlyStatusHandler.OperationStatus operationStatus =
      ReadonlyStatusHandler.getInstance(bundle.getProject()).ensureFilesWritable(Collections.singletonList(bundle.getVirtualFile()));
    if (operationStatus.hasReadonlyFiles()) {
      return false;
    }
    CommandProcessor.getInstance().executeCommand(
      bundle.getProject(),
      () -> {
        UndoUtil.markPsiFileForUndo(formFile);
        ApplicationManager.getApplication().runWriteAction(() -> {
          try {
            bundle.addProperty(name, value);
          }
          catch (IncorrectOperationException e1) {
            LOG.error(e1);
          }
        });
      }, UIDesignerBundle.message("command.create.property"), null);
    return true;
  }

  /**
   * @return edited descriptor. If initial descriptor was {@code null}
   * and user didn't change anything then this method returns {@code null}.
   */
  @Nullable
  StringDescriptor getDescriptor(){
    if(myForm.myRbString.isSelected()){ // plain value
      final String value = myForm.myTfValue.getText();
      if(myValue == null && value.isEmpty()){
        return null;
      }
      else{
        final StringDescriptor stringDescriptor = StringDescriptor.create(value);
        stringDescriptor.setNoI18n(myForm.myNoI18nCheckbox.isSelected());
        return stringDescriptor;
      }
    }
    else{ // bundled value
      final String bundleName = myForm.myTfBundleName.getText();
      final String key = myForm.myTfKey.getText();
      return new StringDescriptor(bundleName, key);
    }
  }

  /**
   * Applies specified descriptor to the proper card
   */
  private void setValue(final StringDescriptor descriptor){
    myValue = descriptor;
    final CardLayout cardLayout = (CardLayout)myForm.myCardHolder.getLayout();
    if(descriptor == null || descriptor.getValue() != null){ // trivial descriptor
      myForm.myRbString.setSelected(true);
      myForm.showStringDescriptor(descriptor);
      cardLayout.show(myForm.myCardHolder, CARD_STRING);
    }
    else{ // bundled property
      myForm.myRbResourceBundle.setSelected(true);
      myForm.showResourceBundleDescriptor(descriptor);
      cardLayout.show(myForm.myCardHolder, CARD_BUNDLE);
    }
  }

  @Override
  protected JComponent createCenterPanel() {
    return myForm.myPanel;
  }

  private final class MyForm {
    private final JRadioButton myRbString;
    private final JRadioButton myRbResourceBundle;
    private final JPanel myCardHolder;
    private final JPanel myPanel;
    private final JTextArea myTfValue;
    private final JCheckBox myNoI18nCheckbox;
    private final TextFieldWithBrowseButton myTfBundleName;
    private final TextFieldWithBrowseButton myTfKey;
    private final JTextField myTfRbValue;
    private final JLabel myLblKey;
    private final JLabel myLblBundleName;

    MyForm() {
      {
        // GUI initializer generated by IntelliJ IDEA GUI Designer
        // >>> IMPORTANT!! <<<
        // DO NOT EDIT OR ADD ANY CODE HERE!
        myPanel = new JPanel();
        myPanel.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        myRbString = new JRadioButton();
        myRbString.setMargin(new Insets(2, 0, 2, 2));
        this.$$$loadButtonText$$$(myRbString, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "radio.string"));
        myPanel.add(myRbString, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                    GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                    GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myRbResourceBundle = new JRadioButton();
        myRbResourceBundle.setMargin(new Insets(2, 0, 2, 2));
        this.$$$loadButtonText$$$(myRbResourceBundle,
                                  this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "radio.resource.bundle"));
        myPanel.add(myRbResourceBundle, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                            GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                            GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myCardHolder = new JPanel();
        myCardHolder.setLayout(new CardLayout(0, 0));
        myPanel.add(myCardHolder, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null,
                                                      null,
                                                      null, 0, false));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(3, 1, new Insets(0, 0, 0, 0), -1, -1));
        myCardHolder.add(panel1, "string");
        final JLabel label1 = new JLabel();
        this.$$$loadLabelText$$$(label1, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "editbox.value.2"));
        panel1.add(label1,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myNoI18nCheckbox = new JCheckBox();
        myNoI18nCheckbox.setSelected(false);
        this.$$$loadButtonText$$$(myNoI18nCheckbox,
                                  this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "uidesigner.string.no.i18n"));
        panel1.add(myNoI18nCheckbox, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                         GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                                         GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JBScrollPane jBScrollPane1 = new JBScrollPane();
        panel1.add(jBScrollPane1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                      GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                      null, null, 0, false));
        myTfValue = new JTextArea();
        myTfValue.setLineWrap(true);
        myTfValue.setWrapStyleWord(true);
        jBScrollPane1.setViewportView(myTfValue);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        myCardHolder.add(panel2, "bundle");
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW,
                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null,
                                               null,
                                               0, false));
        myLblKey = new JLabel();
        this.$$$loadLabelText$$$(myLblKey, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "editbox.key"));
        panel3.add(myLblKey,
                   new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        this.$$$loadLabelText$$$(label2, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "editbox.value"));
        panel3.add(label2,
                   new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myLblBundleName = new JLabel();
        this.$$$loadLabelText$$$(myLblBundleName, this.$$$getMessageFromBundle$$$("messages/UIDesignerBundle", "editbox.bundle.name"));
        panel3.add(myLblBundleName,
                   new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED,
                                       GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        myTfRbValue = new JTextField();
        myTfRbValue.setColumns(30);
        panel3.add(myTfRbValue, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                    GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null,
                                                    null,
                                                    0, false));
        myTfKey = new TextFieldWithBrowseButton();
        myTfKey.setEditable(false);
        myTfKey.setEnabled(true);
        panel3.add(myTfKey, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                new Dimension(150, -1), null, 0, false));
        myTfBundleName = new TextFieldWithBrowseButton();
        myTfBundleName.setEditable(false);
        panel3.add(myTfBundleName, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL,
                                                       GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null,
                                                       new Dimension(150, -1), null, 0, false));
        final Spacer spacer1 = new Spacer();
        panel2.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                                GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        label2.setLabelFor(myTfRbValue);
        ButtonGroup buttonGroup;
        buttonGroup = new ButtonGroup();
        buttonGroup.add(myRbString);
        buttonGroup.add(myRbResourceBundle);
      }
      myRbString.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            CardLayout cardLayout = (CardLayout)myCardHolder.getLayout();
            cardLayout.show(myCardHolder, CARD_STRING);
          }
        }
      );

      myRbResourceBundle.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            if (!myDefaultBundleInitialized) {
              myDefaultBundleInitialized = true;
              Set<String> bundleNames = FormEditingUtil.collectUsedBundleNames(myEditor.getRootContainer());
              if (!bundleNames.isEmpty()) {
                myTfBundleName.setText(ArrayUtilRt.toStringArray(bundleNames)[0]);
              }
            }
            CardLayout cardLayout = (CardLayout)myCardHolder.getLayout();
            cardLayout.show(myCardHolder, CARD_BUNDLE);
          }
        }
      );

      setupResourceBundleCard();
    }

    private static Method $$$cachedGetBundleMethod$$$ = null;

    /** @noinspection ALL */
    private String $$$getMessageFromBundle$$$(String path, String key) {
      ResourceBundle bundle;
      try {
        Class<?> thisClass = this.getClass();
        if ($$$cachedGetBundleMethod$$$ == null) {
          Class<?> dynamicBundleClass = thisClass.getClassLoader().loadClass("com.intellij.DynamicBundle");
          $$$cachedGetBundleMethod$$$ = dynamicBundleClass.getMethod("getBundle", String.class, Class.class);
        }
        bundle = (ResourceBundle)$$$cachedGetBundleMethod$$$.invoke(null, path, thisClass);
      }
      catch (Exception e) {
        bundle = ResourceBundle.getBundle(path);
      }
      return bundle.getString(key);
    }

    /** @noinspection ALL */
    private void $$$loadLabelText$$$(JLabel component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setDisplayedMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    private void $$$loadButtonText$$$(AbstractButton component, String text) {
      StringBuffer result = new StringBuffer();
      boolean haveMnemonic = false;
      char mnemonic = '\0';
      int mnemonicIndex = -1;
      for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '&') {
          i++;
          if (i == text.length()) break;
          if (!haveMnemonic && text.charAt(i) != '&') {
            haveMnemonic = true;
            mnemonic = text.charAt(i);
            mnemonicIndex = result.length();
          }
        }
        result.append(text.charAt(i));
      }
      component.setText(result.toString());
      if (haveMnemonic) {
        component.setMnemonic(mnemonic);
        component.setDisplayedMnemonicIndex(mnemonicIndex);
      }
    }

    /** @noinspection ALL */
    public JComponent $$$getRootComponent$$$() { return myPanel; }

    private void setupResourceBundleCard() {
      // Enable keyboard pressing
      myTfBundleName.registerKeyboardAction(
        new AbstractAction() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            myTfBundleName.getButton().doClick();
          }
        },
        KeyStroke.getKeyStroke(myLblBundleName.getDisplayedMnemonic(), InputEvent.ALT_DOWN_MASK),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );

      myTfBundleName.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            Project project = myEditor.getProject();
            final String bundleNameText = myTfBundleName.getText().replace('/', '.');
            PropertiesFile file = PropertiesUtilBase.getPropertiesFile(bundleNameText, myEditor.getModule(), myLocale);
            PsiFile initialPropertiesFile = file == null ? null : file.getContainingFile();
            final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesScope(myEditor.getModule());
            TreeFileChooser fileChooser = TreeFileChooserFactory.getInstance(project)
              .createFileChooser(UIDesignerBundle.message("title.choose.properties.file"), initialPropertiesFile,
                                 PropertiesFileType.INSTANCE, new TreeFileChooser.PsiFileFilter() {
                  @Override
                  public boolean accept(PsiFile file) {
                    final VirtualFile virtualFile = file.getVirtualFile();
                    return virtualFile != null && moduleScope.contains(virtualFile);
                  }
                });
            fileChooser.showDialog();
            PsiFile selectedFile = fileChooser.getSelectedFile();
            PropertiesFile propertiesFile = selectedFile instanceof PropertiesFile ? (PropertiesFile)selectedFile : null;
            if (propertiesFile == null) {
              return;
            }
            final String bundleName = FormReferenceProvider.getBundleName(propertiesFile);
            if (bundleName == null) {
              return;
            }
            myTfBundleName.setText(bundleName);
          }
        }
      );

      // Enable keyboard pressing
      myTfKey.registerKeyboardAction(
        new AbstractAction() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            myTfKey.getButton().doClick();
          }
        },
        KeyStroke.getKeyStroke(myLblKey.getDisplayedMnemonic(), InputEvent.ALT_DOWN_MASK),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );

      myTfKey.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(final ActionEvent e) {
            // 1. Check that bundle exist. Otherwise we cannot show key chooser
            final String bundleName = myTfBundleName.getText();
            if (bundleName.isEmpty()) {
              Messages.showErrorDialog(
                UIDesignerBundle.message("error.specify.bundle.name"),
                CommonBundle.getErrorTitle()
              );
              return;
            }
            final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(myEditor.getProject());
            final PropertiesFile bundle = manager.findPropertiesFile(myEditor.getModule(), bundleName.replace('/', '.'), myLocale);
            if (bundle == null) {
              Messages.showErrorDialog(
                UIDesignerBundle.message("error.bundle.does.not.exist", bundleName),
                CommonBundle.getErrorTitle()
              );
              return;
            }

            // 2. Show key chooser
            final KeyChooserDialog dialog = new KeyChooserDialog(
              myTfKey,
              bundle,
              bundleName,
              myTfKey.getText(), // key to preselect
              myEditor
            );
            if (!dialog.showAndGet()) {
              return;
            }

            // 3. Apply new key/value
            final StringDescriptor descriptor = dialog.getDescriptor();
            if (descriptor == null) {
              return;
            }
            myTfKey.setText(descriptor.getKey());
            myTfRbValue.setText(descriptor.getResolvedValue());
          }
        }
      );
    }

    public void showStringDescriptor(final @Nullable StringDescriptor descriptor) {
      myTfValue.setText(StringDescriptorManager.getInstance(myEditor.getModule()).resolve(descriptor, myLocale));
      myNoI18nCheckbox.setSelected(descriptor != null && descriptor.isNoI18n());
    }

    public void showResourceBundleDescriptor(final @NotNull StringDescriptor descriptor) {
      final String key = descriptor.getKey();
      LOG.assertTrue(key != null);
      myTfBundleName.setText(descriptor.getBundleName());
      myTfKey.setText(key);
      myTfRbValue.setText(StringDescriptorManager.getInstance(myEditor.getModule()).resolve(descriptor, myLocale));
    }
  }
}
