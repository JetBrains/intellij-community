// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.ColoredListCellRenderer;
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
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;

import static com.intellij.ui.render.RenderersKt.fontInfoRenderer;


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
    myFontNameList.setCellRenderer(fontInfoRenderer(false));
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
      @Override
      public void valueChanged(ListSelectionEvent e) {
        final Integer selValue = Integer.valueOf(myFontSizeList.getSelectedValue().toString());
        myFontSizeEdit.setValue(selValue);
        updateValue();
      }
    });
    myFontSizeEdit.setModel(new SpinnerNumberModel(3, 3, 96, 1));
    myFontSizeEdit.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        @NlsSafe String value = myFontSizeEdit.getValue().toString();
        myFontSizeList.setSelectedValue(value, true);
        updateValue();
      }
    });
    mySwingFontList.setListData(collectSwingFontDescriptors());
    mySwingFontList.setCellRenderer(new ColoredListCellRenderer() {
      @Override
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FontDescriptor descriptor = (FontDescriptor) value;
        clear();
        @NlsSafe String font = descriptor.getSwingFont();
        append(font, selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        append(" (" + fontToString(UIManager.getFont(font)) + ")",
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    });
    mySwingFontList.addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        myValue = (FontDescriptor)mySwingFontList.getSelectedValue();
        updatePreview();
        //showFont(myValue.getResolvedFont());
      }
    });

    myFontNameCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myFontNameList.setEnabled(myFontNameCheckbox.isSelected());
        updateValue();
      }
    });
    myFontStyleCheckbox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(ItemEvent e) {
        myFontStyleList.setEnabled(myFontStyleCheckbox.isSelected());
        updateValue();
      }
    });
    myFontSizeCheckbox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myFontSizeList.setEnabled(myFontSizeCheckbox.isSelected());
        myFontSizeEdit.setEnabled(myFontSizeCheckbox.isSelected());
        updateValue();
      }
    });
  }

  private static @NlsSafe String fontToString(final Font font) {
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
    result.sort(Comparator.comparing(FontDescriptor::getSwingFont));
    return result.toArray(new FontDescriptor[0]);
  }

  public FontDescriptor getValue() {
    return myValue;
  }

  public void setValue(final @NotNull FontDescriptor value) {
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
          myFontSizeEdit.setValue(Integer.valueOf(value.getFontSize()));
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

  @Override
  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    private final JTextField myTextField;

    MyListSelectionListener(final JTextField textField) {
      myTextField = textField;
    }

    @Override
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
