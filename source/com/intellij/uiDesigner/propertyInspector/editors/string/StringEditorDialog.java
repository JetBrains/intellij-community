package com.intellij.uiDesigner.propertyInspector.editors.string;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.uiDesigner.ResourceBundleChooserDialog;
import com.intellij.uiDesigner.ResourceBundleLoader;
import com.intellij.uiDesigner.lw.StringDescriptor;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ResourceBundle;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class StringEditorDialog extends DialogWrapper{
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.propertyInspector.editors.string.StringEditorDialog");

  private final Module myModule;
  /** Descriptor to be edited */
  private StringDescriptor myValue;
  private final MyForm myForm;

  StringEditorDialog(
    final Component parent, 
    final StringDescriptor descriptor,
    final Module module
  ) {
    super(parent, true);

    if (module == null) {
      throw new IllegalArgumentException("module cannot be null");
    }

    myModule = module;

    myForm = new MyForm();
    setTitle("Edit Text");
    setValue(descriptor);

    init(); /* run initialization proc */
  }

  protected String getDimensionServiceKey() {
    return this.getClass().getName();
  }

  public JComponent getPreferredFocusedComponent() {
    if(myForm.myRbString.isSelected()){
      return myForm.myStringCard.myTfValue;
    }
    else{
      return super.getPreferredFocusedComponent();
    }
  }

  /**
   * @return edited descriptor. If initial descriptor was <code>null</code>
   * and user didn't change anything then this method returns <code>null</code>.
   */
  StringDescriptor getDescriptor(){
    if(myForm.myRbString.isSelected()){ // plain value
      final String value = myForm.myStringCard.myTfValue.getText();
      if(myValue == null && value.length() == 0){
        return null;
      }
      else{
        return StringDescriptor.create(value);
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
      cardLayout.show(myForm.myCardHolder, "string");
    }
    else{ // bundled property
      myForm.myRbResourceBundle.setSelected(true);
      myForm.myResourceBundleCard.setDescriptor(descriptor);
      cardLayout.show(myForm.myCardHolder, "bundle");
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
      myCardHolder.add(myStringCard.myPanel, "string");
      myCardHolder.add(myResourceBundleCard.myPanel, "bundle");

      myRbString.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            cardLayout.show(myCardHolder, "string");
          }
        }
      );

      myRbResourceBundle.addActionListener(
        new ActionListener() {
          public void actionPerformed(final ActionEvent e) {
            cardLayout.show(myCardHolder, "bundle");
          }
        }
      );
    }
  }

  private final class MyStringCard{
    private JTextField myTfValue;
    private JPanel myPanel;
    private JLabel myLblValue;

    public MyStringCard() {
      myLblValue.setLabelFor(myTfValue);
    }

    public void setDescriptor(final StringDescriptor descriptor){
      myTfValue.setText(ResourceBundleLoader.resolve(myModule, descriptor));
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
            final ResourceBundleChooserDialog dialog = new ResourceBundleChooserDialog(myModule.getProject(), null);
            dialog.show();
            if(!dialog.isOK()){
              return;
            }
            final String bundleName = dialog.getBundleName();
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
                "Please specify name of the resource bundle",
                "Error"
              );
              return;
            }
            final ResourceBundle bundle = ResourceBundleLoader.getResourceBundle(myModule, bundleName);
            if(bundle == null){
              Messages.showErrorDialog(
                "Bundle \"" + bundleName + "\" does not exist",
                "Error"
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

    public void setDescriptor(final StringDescriptor descriptor){
      LOG.assertTrue(descriptor != null);
      final String key = descriptor.getKey();
      LOG.assertTrue(key != null);
      myTfBundleName.setText(descriptor.getBundleName());
      myTfKey.setText(key);
      myTfValue.setText(ResourceBundleLoader.resolve(myModule, descriptor));
    }
  }
}
