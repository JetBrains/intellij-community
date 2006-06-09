package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.CommonBundle;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeFileChooser;
import com.intellij.lang.properties.PropertiesReferenceManager;
import com.intellij.lang.properties.PropertiesUtil;
import com.intellij.lang.properties.psi.PropertiesElementFactory;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.ReferenceUtil;
import com.intellij.uiDesigner.StringDescriptorManager;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
public final class StringEditorDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.editors.string.StringEditorDialog");

  @NonNls private static final String CARD_STRING = "string";
  @NonNls private static final String CARD_BUNDLE = "bundle";

  private GuiEditor myEditor;
  /** Descriptor to be edited */
  private StringDescriptor myValue;
  private final MyForm myForm;
  private Locale myLocale;
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

  protected String getDimensionServiceKey() {
    return getClass().getName();
  }

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
      if (descriptor != null && descriptor.getKey().length() > 0) {
        final String value = myForm.myTfRbValue.getText();
        final PropertiesFile propFile = getPropertiesFile(descriptor);
        if (propFile != null && propFile.findPropertyByKey(descriptor.getKey()) == null) {
          saveCreatedProperty(propFile, descriptor.getKey(), value, myEditor);
        }
        else {
          saveModifiedPropertyValue(myEditor.getModule(), descriptor, myLocale, value, myEditor.getPsiFile());
        }
      }
    }
    super.doOKAction();
  }

  private PropertiesFile getPropertiesFile(final StringDescriptor descriptor) {
    final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(myEditor.getProject());
    return manager.findPropertiesFile(myEditor.getModule(), descriptor.getDottedBundleName(), myLocale);
  }

  public static void saveModifiedPropertyValue(final Module module, final StringDescriptor descriptor,
                                               final Locale locale, final String editedValue, final PsiFile formFile) {
    final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(module.getProject());
    final PropertiesFile propFile = manager.findPropertiesFile(module, descriptor.getDottedBundleName(), locale);
    if (propFile != null) {
      final Property propertyByKey = propFile.findPropertyByKey(descriptor.getKey());
      if (propertyByKey != null && !editedValue.equals(propertyByKey.getValue())) {
        final Collection<PsiReference> references = new ArrayList<PsiReference>();
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
          new Runnable() {
            public void run() {
             references.addAll(ReferencesSearch.search(propertyByKey).findAll());
            }
          }, UIDesignerBundle.message("edit.text.searching.references"), false, module.getProject()
        );

        if (references.size() > 1) {
          final int rc = Messages.showYesNoDialog(module.getProject(), UIDesignerBundle.message("edit.text.multiple.usages",
                                                                                                propertyByKey.getKey(), references.size()),
                                                  UIDesignerBundle.message("edit.text.multiple.usages.title"), Messages.getWarningIcon());
          if (rc != OK_EXIT_CODE) {
            return;
          }
        }
        final ReadonlyStatusHandler.OperationStatus operationStatus =
          ReadonlyStatusHandler.getInstance(module.getProject()).ensureFilesWritable(propFile.getVirtualFile());
        if (operationStatus.hasReadonlyFiles()) {
          return;
        }
        CommandProcessor.getInstance().executeCommand(
          module.getProject(),
          new Runnable() {
            public void run() {
              UndoManager.getInstance(module.getProject()).markDocumentForUndo(formFile);
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  PsiDocumentManager.getInstance(module.getProject()).commitAllDocuments();
                  try {
                    propFile.findPropertyByKey(descriptor.getKey()).setValue(editedValue);
                  }
                  catch (IncorrectOperationException e) {
                    LOG.error(e);
                  }
                }
              });
            }
          }, UIDesignerBundle.message("command.update.property"), FormEditingUtil.getNextSaveUndoGroupId(module.getProject()));
      }
    }
  }

  public static boolean saveCreatedProperty(final PropertiesFile bundle, final String name, final String value,
                                            final GuiEditor editor) {
    final Property property = PropertiesElementFactory.createProperty(bundle.getProject(), name, value);
    final ReadonlyStatusHandler.OperationStatus operationStatus =
      ReadonlyStatusHandler.getInstance(bundle.getProject()).ensureFilesWritable(bundle.getVirtualFile());
    if (operationStatus.hasReadonlyFiles()) {
      return false;
    }
    final Object groupId = FormEditingUtil.getNextSaveUndoGroupId(bundle.getProject());
    LOG.debug("StringEditorDialog.saveCreatedProperty(): group ID=" + groupId);
    CommandProcessor.getInstance().executeCommand(
      bundle.getProject(),
      new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                bundle.addProperty(property);
              }
              catch (IncorrectOperationException e1) {
                LOG.error(e1);
              }
            }
          });
        }
      }, UIDesignerBundle.message("command.create.property"), groupId);
    return true;
  }

  /**
   * @return edited descriptor. If initial descriptor was <code>null</code>
   * and user didn't change anything then this method returns <code>null</code>.
   */
  @Nullable
  StringDescriptor getDescriptor(){
    if(myForm.myRbString.isSelected()){ // plain value
      final String value = myForm.myTfValue.getText();
      if(myValue == null && value.length() == 0){
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

  protected JComponent createCenterPanel() {
    return myForm.myPanel;
  }

  private final class MyForm{
    private JRadioButton myRbString;
    private JRadioButton myRbResourceBundle;
    private JPanel myCardHolder;
    private JPanel myPanel;
    private JTextArea myTfValue;
    private JCheckBox myNoI18nCheckbox;
    private TextFieldWithBrowseButton myTfBundleName;
    private TextFieldWithBrowseButton myTfKey;
    private JTextField myTfRbValue;
    private JLabel myLblKey;
    private JLabel myLblBundleName;

    public MyForm() {
      myRbString.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            CardLayout cardLayout = (CardLayout) myCardHolder.getLayout();
            cardLayout.show(myCardHolder, CARD_STRING);
          }
        }
      );

      myRbResourceBundle.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            if (!myDefaultBundleInitialized) {
              myDefaultBundleInitialized = true;
              Set<String> bundleNames = FormEditingUtil.collectUsedBundleNames(myEditor.getRootContainer());
              if (bundleNames.size() > 0) {
                myTfBundleName.setText(bundleNames.toArray(new String[bundleNames.size()]) [0]);
              }
            }
            CardLayout cardLayout = (CardLayout) myCardHolder.getLayout();
            cardLayout.show(myCardHolder, CARD_BUNDLE);
          }
        }
      );

      setupResourceBundleCard();
    }

    private void setupResourceBundleCard() {
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
            Project project = myEditor.getProject();
            final String bundleNameText = myTfBundleName.getText().replace('/', '.');
            PsiFile initialPropertiesFile = PropertiesUtil.getPropertiesFile(bundleNameText, myEditor.getModule(), myLocale);
            final GlobalSearchScope moduleScope = GlobalSearchScope.moduleWithDependenciesScope(myEditor.getModule());
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
            final PropertiesReferenceManager manager = PropertiesReferenceManager.getInstance(myEditor.getProject());
            final PropertiesFile bundle = manager.findPropertiesFile(myEditor.getModule(), bundleName.replace('/', '.'), myLocale);
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
              myTfKey.getText(), // key to preselect
              myEditor
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
            myTfRbValue.setText(descriptor.getResolvedValue());
          }
        }
      );
    }

    public void showStringDescriptor(@Nullable final StringDescriptor descriptor) {
      myTfValue.setText(StringDescriptorManager.getInstance(myEditor.getModule()).resolve(descriptor, myLocale));
      myNoI18nCheckbox.setSelected(descriptor != null && descriptor.isNoI18n());
    }

    public void showResourceBundleDescriptor(@NotNull final StringDescriptor descriptor) {
      final String key = descriptor.getKey();
      LOG.assertTrue(key != null);
      myTfBundleName.setText(descriptor.getBundleName());
      myTfKey.setText(key);
      myTfRbValue.setText(StringDescriptorManager.getInstance(myEditor.getModule()).resolve(descriptor, myLocale));
    }
  }
}
