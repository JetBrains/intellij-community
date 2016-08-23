/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.FontInfoRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;
import com.intellij.util.ui.FontInfo;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Font;
import java.awt.event.ItemListener;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

/**
 * @author yole
 */
public class FontEditorDialog extends DialogWrapper {
  private final Model myModel = new Model();
  private JList myFontNameList;
  private JList myFontStyleList;
  private JList myFontSizeList;
  private JPanel myRootPane;
  private JLabel myPreviewTextLabel;
  private JTextField myFontNameEdit;
  private JTextField myFontStyleEdit;
  private JSpinner myFontSizeEdit;
  private JList mySwingFontList;
  private JTabbedPane myTabbedPane;
  private JCheckBox myFontNameCheckbox;
  private JCheckBox myFontStyleCheckbox;
  private JCheckBox myFontSizeCheckbox;
  private FontDescriptor myValue;

  protected FontEditorDialog(final Project project, String propertyName) {
    super(project, false);
    init();
    setTitle(UIDesignerBundle.message("font.chooser.title", propertyName));
    myFontNameList.setModel(myModel);
    myFontNameList.setCellRenderer(new FontInfoRenderer());
    myFontNameList.addListSelectionListener(new MyListSelectionListener(myFontNameEdit));
    myFontStyleList.setListData(new String[] {
      UIDesignerBundle.message("font.chooser.regular"),
      UIDesignerBundle.message("font.chooser.bold"),
      UIDesignerBundle.message("font.chooser.italic"),
      UIDesignerBundle.message("font.chooser.bold.italic")
    });
    myFontStyleList.addListSelectionListener(new MyListSelectionListener(myFontStyleEdit));
    myFontSizeList.setListData(UIUtil.getStandardFontSizes());
    myFontSizeList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final Integer selValue = Integer.valueOf(myFontSizeList.getSelectedValue().toString());
        myFontSizeEdit.setValue(selValue);
        updateValue();
      }
    });
    myFontSizeEdit.setModel(new SpinnerNumberModel(3, 3, 96, 1));
    myFontSizeEdit.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myFontSizeList.setSelectedValue(myFontSizeEdit.getValue().toString(), true);
        updateValue();
      }
    });
    mySwingFontList.setListData(collectSwingFontDescriptors());
    mySwingFontList.setCellRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FontDescriptor descriptor = (FontDescriptor) value;
        clear();
        append(descriptor.getSwingFont(),
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        append(" (" + fontToString(UIManager.getFont(descriptor.getSwingFont())) + ")",
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    });
    mySwingFontList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myValue = (FontDescriptor)mySwingFontList.getSelectedValue();
        updatePreview();
        //showFont(myValue.getResolvedFont());
      }
    });

    myFontNameCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myFontNameList.setEnabled(myFontNameCheckbox.isSelected());
        updateValue();
      }
    });
    myFontStyleCheckbox.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        myFontStyleList.setEnabled(myFontStyleCheckbox.isSelected());
        updateValue();
      }
    });
    myFontSizeCheckbox.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        myFontSizeList.setEnabled(myFontSizeCheckbox.isSelected());
        myFontSizeEdit.setEnabled(myFontSizeCheckbox.isSelected());
        updateValue();
      }
    });
  }

  private static String fontToString(final Font font) {
    StringBuilder result = new StringBuilder(font.getFamily());
    result.append(" ").append(font.getSize());
    if ((font.getStyle() & Font.BOLD) != 0) {
      result.append(" ").append(UIDesignerBundle.message("font.chooser.bold"));
    }
    if ((font.getStyle() & Font.ITALIC) != 0) {
      result.append(" ").append(UIDesignerBundle.message("font.chooser.bold"));
    }
    return result.toString();
  }

  private static FontDescriptor[] collectSwingFontDescriptors() {
    ArrayList<FontDescriptor> result = new ArrayList<>();
    UIDefaults defaults = UIManager.getDefaults();
    Enumeration e = defaults.keys ();
    while(e.hasMoreElements()) {
      Object key = e.nextElement();
      Object value = defaults.get(key);
      if (key instanceof String && value instanceof Font) {
        result.add(FontDescriptor.fromSwingFont((String) key));
      }
    }
    Collections.sort(result, (o1, o2) -> o1.getSwingFont().compareTo(o2.getSwingFont()));
    return result.toArray(new FontDescriptor[result.size()]);
  }

  public FontDescriptor getValue() {
    return myValue;
  }

  public void setValue(@NotNull final FontDescriptor value) {
    myValue = value;
    if (value.getSwingFont() != null) {
      myTabbedPane.setSelectedIndex(1);
      mySwingFontList.setSelectedValue(myValue, true);
    }
    else {
      myFontNameCheckbox.setSelected(value.getFontName() != null);
      myFontSizeCheckbox.setSelected(value.getFontSize() >= 0);
      myFontStyleCheckbox.setSelected(value.getFontStyle() >= 0);
      myFontNameList.setSelectedValue(myModel.findElement(value.getFontName()), true);
      myFontStyleList.setSelectedIndex(value.getFontStyle());
      if (value.getFontSize() >= 0) {
        myFontSizeList.setSelectedValue(Integer.toString(value.getFontSize()), true);
        if (myFontSizeList.getSelectedIndex() < 0) {
          myFontSizeEdit.setValue(new Integer(value.getFontSize()));
        }
      }
      else {
        myFontSizeList.setSelectedIndex(-1);
        myFontSizeEdit.setValue(0);
      }
    }
  }

  private void updateValue() {
    final int fontSize = ((Integer)myFontSizeEdit.getValue()).intValue();
    myValue = new FontDescriptor(myFontNameCheckbox.isSelected() ? toString(myFontNameList.getSelectedValue()) : null,
                                 myFontStyleCheckbox.isSelected() ? myFontStyleList.getSelectedIndex() : -1,
                                 myFontSizeCheckbox.isSelected() ? fontSize : -1);
    updatePreview();
  }

  private void updatePreview() {
    myPreviewTextLabel.setText(IntroFontProperty.descriptorToString(myValue));
    myPreviewTextLabel.setFont(myValue.getResolvedFont(myRootPane.getFont()));
  }

  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    private final JTextField myTextField;

    public MyListSelectionListener(final JTextField textField) {
      myTextField = textField;
    }

    public void valueChanged(ListSelectionEvent e) {
      JList sourceList = (JList) e.getSource();
      final Object selValue = sourceList.getSelectedValue();
      if (selValue != null) {
        myTextField.setText(selValue.toString());
      }
      else {
        myTextField.setText("");
      }
      updateValue();
    }
  }

  private static String toString(Object object) {
    return object == null ? null : object.toString();
  }

  private static final class Model extends AbstractListModel {
    private final List<FontInfo> myList = FontInfo.getAll(false);

    @Override
    public int getSize() {
      return myList.size();
    }

    @Override
    public FontInfo getElementAt(int index) {
      return myList.get(index);
    }

    public FontInfo findElement(String name) {
      for (FontInfo info : myList) {
        if (info.toString().equalsIgnoreCase(name)) {
          return info;
        }
      }
      return null;
    }
  }
}
