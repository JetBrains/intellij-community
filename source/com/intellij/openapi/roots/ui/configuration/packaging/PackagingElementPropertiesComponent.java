package com.intellij.openapi.roots.ui.configuration.packaging;

import com.intellij.openapi.deployment.ContainerElement;
import com.intellij.openapi.deployment.PackagingMethod;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

/**
 * @author nik
 */
public class PackagingElementPropertiesComponent {
  private JPanel myMainPanel;
  private JComboBox myPackagingMethodBox;
  private JTextField myOutputPathField;
  private JLabel myPackagingMethodLabel;
  private JLabel myOutputPathLabel;
  private JLabel myElementNameLabel;
  private JPanel myLabelsPanel;
  private JPanel myFieldPanel;
  private final PackagingEditorPolicy myEditorPolicy;
  private PackagingMethod myLastSelectedMethod;
  private PackagingEditorPolicy.AdditionalPropertiesConfigurable myAdditionalPropertiesConfigurable;
  private PackagingElementsToEditInfo myElementsInfo;
  private Map<ContainerElement,String> myPathTails;
  private PackagingEditorListener myListener;

  private PackagingElementPropertiesComponent(PackagingElementsToEditInfo elementsInfo, PackagingEditorPolicy editorPolicy,
                                              final @Nullable PackagingEditorListener listener) {
    myElementsInfo = elementsInfo;
    myListener = listener;
    myElementNameLabel.setText(elementsInfo.getElementText());
    myEditorPolicy = editorPolicy;
    if (elementsInfo.getAllowedPackagingMethods().length > 1 && elementsInfo.getPackagingMethod() != null) {
      for (PackagingMethod method : elementsInfo.getAllowedPackagingMethods()) {
        myPackagingMethodBox.addItem(method);
      }
      myPackagingMethodBox.setRenderer(new PackagingMethodListCellRenderer());
      myPackagingMethodBox.setSelectedItem(myLastSelectedMethod = elementsInfo.getPackagingMethod());
      myPackagingMethodBox.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          updateOutputPath();
        }
      });
    }
    else {
      myPackagingMethodBox.setVisible(false);
      myPackagingMethodLabel.setVisible(false);
    }

    String outputPath = elementsInfo.getRelativePath();
    myPathTails = elementsInfo.getPathTails();
    if (outputPath != null) {
      myOutputPathField.setText(outputPath);
    }
    else {
      myOutputPathField.setVisible(false);
      myOutputPathLabel.setVisible(false);
    }
    List<ContainerElement> elements = elementsInfo.getElements();
    if (elements.size() == 1) {
      ContainerElement element = elements.get(0);
      myAdditionalPropertiesConfigurable = editorPolicy.getAdditionalPropertiesConfigurable(element);
      if (myAdditionalPropertiesConfigurable != null) {
        myLabelsPanel.add(myAdditionalPropertiesConfigurable.getLabelsComponent(), BorderLayout.CENTER);
        myFieldPanel.add(myAdditionalPropertiesConfigurable.getFieldsComponent(), BorderLayout.CENTER);
        myAdditionalPropertiesConfigurable.resetFrom(element);
      }
    }
  }

  private void updateOutputPath() {
    PackagingMethod method = getSelectedMethod();
    if (method != myLastSelectedMethod) {
      Map<ContainerElement, String> paths = new HashMap<ContainerElement, String>();
      for (ContainerElement element : myElementsInfo.getElements()) {
        PackagingMethod oldMethod = element.getPackagingMethod();
        element.setPackagingMethod(method);
        paths.put(element, myEditorPolicy.suggestDefaultRelativePath(element));
        element.setPackagingMethod(oldMethod);
      }
      Pair<String,Map<ContainerElement,String>> pair = PackagingElementsToEditInfo.getPrefixAndSuffixes(paths, myEditorPolicy);
      if (pair != null) {
        myOutputPathField.setText(pair.getFirst());
        myPathTails = pair.getSecond();
      }
      myOutputPathField.setEnabled(pair != null);
      myLastSelectedMethod = method;
    }
  }

  @TestOnly
  public JTextField getOutputPathField() {
    return myOutputPathField;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }

  public void applyChanges() {
    PackagingMethod packagingMethod = getSelectedMethod();
    List<ContainerElement> changedElements = new ArrayList<ContainerElement>();
    if (packagingMethod != null && myElementsInfo.getPackagingMethod() != null) {
      for (ContainerElement element : myElementsInfo.getElements()) {
        element.setPackagingMethod(packagingMethod);
      }
    }
    String text = myOutputPathField.getText();
    if (text.length() == 0) {
      text = "/";
    }
    if (myElementsInfo.getRelativePath() != null) {
      for (ContainerElement element : myElementsInfo.getElements()) {
        String path = text;
        if (myPathTails != null) {
          String tail = myPathTails.get(element);
          if (tail.startsWith("/")) {
            tail = tail.substring(1);
          }
          if (!path.endsWith("/")) {
            path += "/";
          }
          path += tail;
        }
        if (path.length() > 1 && path.endsWith("/")) {
          path = path.substring(0, path.length() - 1);
        }
        element.setURI(FileUtil.toSystemIndependentName(path));
      }
    }
    if (myAdditionalPropertiesConfigurable != null && myElementsInfo.getElements().size() == 1) {
      myAdditionalPropertiesConfigurable.applyTo(myElementsInfo.getElements().get(0));
    }
    if (myListener != null) {
      for (ContainerElement changedElement : changedElements) {
        myListener.packagingMethodChanged(changedElement);
      }
    }
  }

  @Nullable
  private PackagingMethod getSelectedMethod() {
    return (PackagingMethod)myPackagingMethodBox.getSelectedItem();
  }

  @TestOnly
  public JComboBox getPackagingMethodBox() {
    return myPackagingMethodBox;
  }

  public static boolean isEnabled(PackagingElementsToEditInfo elementsToEdit) {
    boolean showPackagingMethodBox = elementsToEdit.getAllowedPackagingMethods().length > 1 && elementsToEdit.getPackagingMethod() != null;
    boolean showOutputPathField = elementsToEdit.getRelativePath() != null;
    return showOutputPathField || showPackagingMethodBox;
  }

  @Nullable
  public static PackagingElementPropertiesComponent createPropertiesComponent(PackagingElementsToEditInfo elementsToEdit,
                                                                              PackagingEditorPolicy editorPolicy,
                                                                              final PackagingEditorListener listener) {
    if (!isEnabled(elementsToEdit)) return null;

    return new PackagingElementPropertiesComponent(elementsToEdit, editorPolicy, listener);
  }

  public static boolean showDialog(final PackagingElementsToEditInfo elementsToEdit, JPanel component, PackagingEditorPolicy policy,
                                   final PackagingEditorListener listener) {
    PackagingElementPropertiesComponent propertiesComponent = createPropertiesComponent(elementsToEdit, policy, listener);
    if (propertiesComponent == null) {
      return false;
    }

    PackagingElementPropertiesDialog dialog = new PackagingElementPropertiesDialog(component, propertiesComponent);
    dialog.show();
    return dialog.isOK();
  }

  private class PackagingMethodListCellRenderer extends DefaultListCellRenderer {
    @Override
      public Component getListCellRendererComponent(final JList list,
                                                  Object value,
                                                  final int index,
                                                  final boolean isSelected,
                                                  final boolean cellHasFocus) {
      ContainerElement element = myElementsInfo.getElements().get(0);
      value = element.getDescriptionForPackagingMethod((PackagingMethod)value);
      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
  }

  public static class PackagingElementPropertiesDialog extends DialogWrapper {
    private PackagingElementPropertiesComponent myPropertiesComponent;

    public PackagingElementPropertiesDialog(JComponent parent, PackagingElementPropertiesComponent propertiesComponent) {
      super(parent, false);
      setTitle(ProjectBundle.message("dialog.title.packaging.edit.properties"));
      myPropertiesComponent = propertiesComponent;
      init();
    }

    protected JComponent createCenterPanel() {
      return myPropertiesComponent.getMainPanel();
    }

    @Override
    protected void doOKAction() {
      myPropertiesComponent.applyChanges();
      super.doOKAction();
    }
  }
}
