package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.CommonBundle;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.Collection;
import java.util.ArrayList;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class StringEditorDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.editors.string.StringEditorDialog");

  @NonNls
  private static final String CARD_STRING = "string";
  @NonNls
  private static final String CARD_BUNDLE = "bundle";

  private final Module myModule;
  /** Descriptor to be edited */
  private StringDescriptor myValue;
  private final MyForm myForm;

  StringEditorDialog(
    final Component parent,
    final StringDescriptor descriptor,
    @NotNull final Module module
  ) {
    super(parent, true);

    myModule = module;

    myForm = new MyForm();
    setTitle(UIDesignerBundle.message("title.edit.text"));
    setValue(descriptor);

    init(); /* run initialization proc */
  }

  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

  public JComponent getPreferredFocusedComponent() {
    if(myForm.myRbString.isSelected()){
      return myForm.myStringCard.myTfValue;
    }
    else{
      return super.getPreferredFocusedComponent();
    }
  }

  @Override protected void doOKAction() {
    if (myForm.myRbResourceBundle.isSelected()) {
      final StringDescriptor descriptor = getDescriptor();
      if (descriptor != null) {
        checkSaveModifiedValue(descriptor);
      }
    }
    super.doOKAction();
  }

  private void checkSaveModifiedValue(final StringDescriptor descriptor) {
    final PropertiesFile propFile = PropertiesUtil.getPropertiesFile(descriptor.getBundleName(), myModule);
    if (propFile != null) {
      final Property propertyByKey = propFile.findPropertyByKey(descriptor.getKey());
      final String editedValue = myForm.myResourceBundleCard.myTfValue.getText();
      if (propertyByKey != null && !editedValue.equals(propertyByKey.getValue())) {
        final Collection<PsiReference> references = new ArrayList<PsiReference>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          new Runnable() {
            public void run() {
             references.addAll(ReferencesSearch.search(propertyByKey).findAll());
            }
          }, UIDesignerBundle.message("edit.text.searching.references"), false, myModule.getProject()
        );

        if (references.size() > 1) {
          final int rc = Messages.showYesNoDialog(myModule.getProject(), UIDesignerBundle.message("edit.text.multiple.usages",
                                                                                                  propertyByKey.getKey(), references.size()),
                                                  UIDesignerBundle.message("edit.text.multiple.usages.title"), Messages.getWarningIcon());
          if (rc != OK_EXIT_CODE) {
            return;
          }
        }
        final ReadonlyStatusHandler.OperationStatus operationStatus =
          ReadonlyStatusHandler.getInstance(myModule.getProject()).ensureFilesWritable(propFile.getVirtualFile());
        if (operationStatus.hasReadonlyFiles()) {
          return;
        }
        CommandProcessor.getInstance().executeCommand(
          myModule.getProject(),
          new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  try {
                    propertyByKey.setValue(editedValue);
                  }
                  catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }
                }
              });
            }
          }, null, null);
      }
    }
  }

  /**
   * @return edited descriptor. If initial descriptor was <code>null</code>
   * and user didn't change anything then this method returns <code>null</code>.
   */
  @Nullable
  StringDescriptor getDescriptor(){
    if(myForm.myRbString.isSelected()){ // plain value
      final String value = myForm.myStringCard.myTfValue.getText();
      if(myValue == null && value.length() == 0){
        return null;
      }
      else{
        final StringDescriptor stringDescriptor = StringDescriptor.create(value);
        stringDescriptor.setNoI18n(myForm.myStringCard.myNoI18nCheckbox.isSelected());
        return stringDescriptor;
      }
    }
    else{ // bundled value
      final String bundleName = myForm.myResourceBundleCard.myTfBundleName.getText();
      final String key = myForm.myResourceBundleCard.myTfKey.getText();
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
      myForm.myStringCard.setDescriptor(descriptor);
      cardLayout.show(myForm.myCardHolder, CARD_STRING);
    }
    else{ // bundled property
      myForm.myRbResourceBundle.setSelected(true);
      myForm.myResourceBundleCard.setDescriptor(descriptor);
      cardLayout.show(myForm.myCardHolder, CARD_BUNDLE);
    }
  }

  protected JComponent createCenterPanel() {
    return myForm.myPanel;
  }

  private final class MyForm{
    private JRadioButton myRbString;
    private JRadioButton myRbResourceBundle;
    private JPanel myCardHolder;
    private JPanel myPanel;
    /** Card with editor for row string value */
    private final MyStringCard myStringCard;
    /** Card with editor for value defined via resource bundle */
    private final MyResourceBundleCard myResourceBundleCard;

    public MyForm() {
      myStringCard = new MyStringCard();
      myResourceBundleCard = new MyResourceBundleCard();

      final ButtonGroup buttonGroup = new ButtonGroup();
      buttonGroup.add(myRbString);
      buttonGroup.add(myRbResourceBundle);

      final CardLayout cardLayout = new CardLayout();
      myCardHolder.setLayout(cardLayout);
      myCardHolder.add(myStringCard.myPanel, CARD_STRING);
      myCardHolder.add(myResourceBundleCard.myPanel, CARD_BUNDLE);

      myRbString.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            cardLayout.show(myCardHolder, CARD_STRING);
          }
        }
      );

      myRbResourceBundle.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            cardLayout.show(myCardHolder, CARD_BUNDLE);
          }
        }
      );
    }
  }

  private final class MyStringCard{
    private JTextField myTfValue;
    private JPanel myPanel;
    private JLabel myLblValue;
    private JCheckBox myNoI18nCheckbox;

    public MyStringCard() {
      myLblValue.setLabelFor(myTfValue);
    }

    public void setDescriptor(@Nullable final StringDescriptor descriptor){
      myTfValue.setText(ReferenceUtil.resolve(myModule, descriptor));
      myNoI18nCheckbox.setSelected(descriptor != null ? descriptor.isNoI18n() : false);
    }
  }

  private final class MyResourceBundleCard{
    private TextFieldWithBrowseButton myTfKey;
    private JTextField myTfValue;
    private JPanel myPanel;
    private TextFieldWithBrowseButton myTfBundleName;
    private JLabel myLblBundleName;
    private JLabel myLblKey;

    public MyResourceBundleCard() {
      // Enable keyboard pressing
      myTfBundleName.registerKeyboardAction(
        new AbstractAction() {
          public void actionPerformed(final ActionEvent e) {
            myTfBundleName.getButton().doClick();
          }
        },
        KeyStroke.getKeyStroke(myLblBundleName.getDisplayedMnemonic(), KeyEvent.ALT_DOWN_MASK),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );

      myTfBundleName.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            Project project = myModule.getProject();
            PsiFile initialPropertiesFile = PropertiesUtil.getPropertiesFile(MyResourceBundleCard.this.myTfBundleName.getText(), myModule);
            final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesScope(myModule);
            TreeFileChooser fileChooser = TreeClassChooserFactory.getInstance(project).createFileChooser(UIDesignerBundle.message("title.choose.properties.file"), initialPropertiesFile,
                                                                                                         StdFileTypes.PROPERTIES, new TreeFileChooser.PsiFileFilter() {
              public boolean accept(PsiFile file) {
                final VirtualFile virtualFile = file.getVirtualFile();
                return virtualFile != null && moduleScope.contains(virtualFile);
              }
            });
            fileChooser.showDialog();
            PropertiesFile propertiesFile = (PropertiesFile)fileChooser.getSelectedFile();
            if (propertiesFile == null) {
              return;
            }
            final String bundleName = ReferenceUtil.getBundleName(propertiesFile);
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
          public void actionPerformed(final ActionEvent e) {
            myTfKey.getButton().doClick();
          }
        },
        KeyStroke.getKeyStroke(myLblKey.getDisplayedMnemonic(), KeyEvent.ALT_DOWN_MASK),
        JComponent.WHEN_IN_FOCUSED_WINDOW
      );

      myTfKey.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            // 1. Check that bundle exist. Otherwise we cannot show key chooser
            final String bundleName = myTfBundleName.getText();
            if(bundleName.length() == 0){
              Messages.showErrorDialog(
                UIDesignerBundle.message("error.specify.bundle.name"),
                CommonBundle.getErrorTitle()
              );
              return;
            }
            final PropertiesFile bundle = PropertiesUtil.getPropertiesFile(bundleName, myModule);
            if(bundle == null){
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
              myTfKey.getText() // key to preselect
            );
            dialog.show();
            if(!dialog.isOK()){
              return;
            }

            // 3. Apply new key/value
            final StringDescriptor descriptor = dialog.getDescriptor();
            if(descriptor == null){
              return;
            }
            myTfKey.setText(descriptor.getKey());
            myTfValue.setText(descriptor.getResolvedValue());
          }
        }
      );
    }

    public void setDescriptor(@NotNull final StringDescriptor descriptor){
      final String key = descriptor.getKey();
      LOG.assertTrue(key != null);
      myTfBundleName.setText(descriptor.getBundleName());
      myTfKey.setText(key);
      myTfValue.setText(ReferenceUtil.resolve(myModule, descriptor));
    }
  }

}
