package com.intellij.uiDesigner.propertyInspector.editors;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.uiDesigner.lw.FontDescriptor;
import com.intellij.uiDesigner.propertyInspector.properties.IntroFontProperty;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;

/**
 * @author yole
 */
public class FontEditorDialog extends DialogWrapper {
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
  private FontDescriptor myValue;

  protected FontEditorDialog(final Project project, String propertyName) {
    super(project, false);
    init();
    setTitle(UIDesignerBundle.message("font.chooser.title", propertyName));
    myFontNameList.setListData(UIUtil.getValidFontNames(true));
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
        myFontSizeEdit.setValue(Integer.valueOf(myFontSizeList.getSelectedValue().toString()));
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
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        FontDescriptor descriptor = (FontDescriptor) value;
        clear();
        append(descriptor.getSwingFont(),
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.SIMPLE_CELL_ATTRIBUTES);
        append(" (" + IntroFontProperty.fontToString(descriptor.getResolvedFont()) + ")",
               selected ? SimpleTextAttributes.SELECTED_SIMPLE_CELL_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES);
      }
    });
    mySwingFontList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        myValue = (FontDescriptor)mySwingFontList.getSelectedValue();
        updatePreview();
        showFont(myValue.getResolvedFont());
      }
    });
  }

  private FontDescriptor[] collectSwingFontDescriptors() {
    ArrayList<FontDescriptor> result = new ArrayList<FontDescriptor>();
    UIDefaults defaults = UIManager.getDefaults();
    Enumeration e = defaults.keys ();
    while(e.hasMoreElements()) {
      Object key = e.nextElement();
      Object value = defaults.get(key);
      if (key instanceof String && value instanceof Font) {
        result.add(FontDescriptor.fromSwingFont((String) key));
      }
    }
    Collections.sort(result, new Comparator<FontDescriptor>() {
      public int compare(final FontDescriptor o1, final FontDescriptor o2) {
        return o1.getSwingFont().compareTo(o2.getSwingFont());
      }
    });
    return result.toArray(new FontDescriptor[result.size()]);
  }

  public FontDescriptor getValue() {
    return myValue;
  }

  public void setValue(@NotNull final FontDescriptor value) {
    myValue = value;
    final Font font = myValue.getFont();
    if (font != null) {
      showFont(font);
    }
    else {
      myTabbedPane.setSelectedIndex(1);
      mySwingFontList.setSelectedValue(myValue, true);
      showFont(myValue.getResolvedFont());
    }
  }

  private void showFont(final Font font) {
    myFontNameList.setSelectedValue(font.getFamily(), true);
    myFontStyleList.setSelectedIndex(font.getStyle());
    final int fontSize = font.getSize();
    myFontSizeList.setSelectedValue(Integer.toString(fontSize), true);
    if (myFontSizeList.getSelectedIndex() < 0) {
      myFontSizeEdit.setValue(new Integer(fontSize));
    }
  }

  private void updateValue() {
    myValue = new FontDescriptor(new Font((String) myFontNameList.getSelectedValue(),
                                          myFontStyleList.getSelectedIndex(),
                                          ((Integer)myFontSizeEdit.getValue()).intValue()));
    updatePreview();
  }

  private void updatePreview() {
    myPreviewTextLabel.setText(IntroFontProperty.fontToString(myValue.getResolvedFont()));
    myPreviewTextLabel.setFont(myValue.getResolvedFont());
  }

  protected JComponent createCenterPanel() {
    return myRootPane;
  }

  private class MyListSelectionListener implements ListSelectionListener {
    private JTextField myTextField;

    public MyListSelectionListener(final JTextField textField) {
      myTextField = textField;
    }

    public void valueChanged(ListSelectionEvent e) {
      JList sourceList = (JList) e.getSource();
      myTextField.setText(sourceList.getSelectedValue().toString());
      updateValue();
    }
  }
}
