package com.intellij.javadoc;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.psi.PsiKeyword;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;

final class JavadocGenerationPanel extends JPanel {
  JPanel myPanel;
  TextFieldWithBrowseButton myTfOutputDir;
  JTextField myOtherOptionsField;
  JTextField myHeapSizeField;
  private JSlider myScopeSlider;
  JCheckBox myHierarchy;
  JCheckBox myNavigator;
  JCheckBox myIndex;
  JCheckBox mySeparateIndex;
  JCheckBox myTagUse;
  JCheckBox myTagAuthor;
  JCheckBox myTagVersion;
  JCheckBox myTagDeprecated;
  JCheckBox myDeprecatedList;
  JCheckBox myOpenInBrowserCheckBox;
  JTextField myLocaleTextField;

  JavadocGenerationPanel() {
    myTfOutputDir.addBrowseFolderListener(JavadocBundle.message("javadoc.generate.output.directory.browse"), null, null, FileChooserDescriptorFactory.createSingleFolderDescriptor());


    myIndex.addChangeListener(
      new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          mySeparateIndex.setEnabled(myIndex.isSelected());
        }
      }
    );

      myTagDeprecated.addChangeListener(
      new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          myDeprecatedList.setEnabled(myTagDeprecated.isSelected());
        }
      }
   );

    //noinspection UseOfObsoleteCollectionType
    Hashtable<Integer, JLabel> labelTable = new Hashtable<Integer, JLabel>();
    labelTable.put(new Integer(1), new JLabel(PsiKeyword.PUBLIC));
    labelTable.put(new Integer(2), new JLabel(PsiKeyword.PROTECTED));
    labelTable.put(new Integer(3), new JLabel(PsiKeyword.PACKAGE));
    labelTable.put(new Integer(4), new JLabel(PsiKeyword.PRIVATE));

    myScopeSlider.setMaximum(4);
    myScopeSlider.setMinimum(1);
    myScopeSlider.setValue(1);
    myScopeSlider.setLabelTable(labelTable);
    myScopeSlider.putClientProperty(UIUtil.JSLIDER_ISFILLED, Boolean.TRUE);
    myScopeSlider.setPreferredSize(new Dimension(80, 50));
    myScopeSlider.setPaintLabels(true);
    myScopeSlider.setSnapToTicks(true);
    myScopeSlider.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        handleSlider();
      }
    });

  }
  private void handleSlider() {
    int value = myScopeSlider.getValue();

    Dictionary labelTable = myScopeSlider.getLabelTable();
    for (Enumeration enumeration = labelTable.keys();  enumeration.hasMoreElements();) {
      Integer key = (Integer)enumeration.nextElement();
      JLabel label = (JLabel)labelTable.get(key);
      label.setForeground(key.intValue() <= value ? Color.black : new Color(100, 100, 100));
    }
  }

  void setScope(String scope) {
    if (PsiKeyword.PUBLIC.equals(scope)) {
      myScopeSlider.setValue(1);
    }
    else if (PsiKeyword.PROTECTED.equals(scope)) {
      myScopeSlider.setValue(3);
    }
    else if (PsiKeyword.PRIVATE.equals(scope)) {
      myScopeSlider.setValue(4);
    }
    else {
      myScopeSlider.setValue(2);
    }
    handleSlider();
  }

  String getScope() {
    switch (myScopeSlider.getValue()) {
      case 1:
        return PsiKeyword.PUBLIC;
      case 2:
        return PsiKeyword.PROTECTED;
      case 3:
        return PsiKeyword.PACKAGE;
      case 4:
        return PsiKeyword.PRIVATE;
      default:
        return null;
    }
  }
}