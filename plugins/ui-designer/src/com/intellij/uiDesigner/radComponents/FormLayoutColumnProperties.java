// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.jgoodies.forms.layout.*;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


public class FormLayoutColumnProperties implements CustomPropertiesPanel {
  private static final Map<String, @Nls String> UNITS_MAP;
  static {
    UNITS_MAP = new LinkedHashMap<>();
    UNITS_MAP.put("px", UIDesignerBundle.message("unit.pixels"));
    UNITS_MAP.put("dlu", UIDesignerBundle.message("unit.dialog.units"));
    UNITS_MAP.put("pt", UIDesignerBundle.message("unit.points"));
    UNITS_MAP.put("in", UIDesignerBundle.message("unit.inches"));
    UNITS_MAP.put("cm", UIDesignerBundle.message("unit.centimeters"));
    UNITS_MAP.put("mm", UIDesignerBundle.message("unit.millimeters"));
  }

  private JPanel myRootPanel;
  private JRadioButton myDefaultRadioButton;
  private JRadioButton myPreferredRadioButton;
  private JRadioButton myMinimumRadioButton;
  private JRadioButton myConstantRadioButton;
  private JComboBox<String> myConstantSizeUnitsCombo;
  private JCheckBox myMinimumCheckBox;
  private JCheckBox myMaximumCheckBox;
  private JSpinner myMaxSizeSpinner;
  private JComboBox<String> myMinSizeUnitsCombo;
  private JComboBox<String> myMaxSizeUnitsCombo;
  private JSpinner myConstantSizeSpinner;
  private JSpinner myMinSizeSpinner;
  private JCheckBox myGrowCheckBox;
  private JSpinner myGrowSpinner;
  private JRadioButton myLeftRadioButton;
  private JRadioButton myCenterRadioButton;
  private JRadioButton myRightRadioButton;
  private JRadioButton myFillRadioButton;
  private JLabel myTitleLabel;
  private JPanel myAlignmentPanel;
  private JPanel mySizePanel;
  private FormLayout myLayout;
  private int myIndex;
  private boolean myIsRow;
  private final List<ChangeListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private boolean myShowing = false;
  private boolean mySaving = false;

  public FormLayoutColumnProperties() {
    String[] unitNames = ArrayUtilRt.toStringArray(UNITS_MAP.keySet());
    myConstantSizeUnitsCombo.setModel(new DefaultComboBoxModel<>(unitNames));
    myConstantSizeUnitsCombo.setRenderer(new UnitListCellRenderer());
    myMinSizeUnitsCombo.setModel(new DefaultComboBoxModel<>(unitNames));
    myMinSizeUnitsCombo.setRenderer(new UnitListCellRenderer());
    myMaxSizeUnitsCombo.setModel(new DefaultComboBoxModel<>(unitNames));
    myMaxSizeUnitsCombo.setRenderer(new UnitListCellRenderer());
    final MyRadioListener listener = new MyRadioListener();
    myDefaultRadioButton.addActionListener(listener);
    myPreferredRadioButton.addActionListener(listener);
    myMinimumRadioButton.addActionListener(listener);
    myConstantRadioButton.addActionListener(listener);

    myMinimumCheckBox.addChangeListener(new MyCheckboxListener(myMinimumCheckBox, myMinSizeUnitsCombo, myMinSizeSpinner));
    myMaximumCheckBox.addChangeListener(new MyCheckboxListener(myMaximumCheckBox, myMaxSizeUnitsCombo, myMaxSizeSpinner));
    myConstantRadioButton.addChangeListener(new MyCheckboxListener(myConstantRadioButton, myConstantSizeUnitsCombo, myConstantSizeSpinner));

    updateOnRadioChange();

    myGrowCheckBox.addChangeListener(new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        myGrowSpinner.setEnabled(myGrowCheckBox.isSelected());
        updateSpec();
      }
    });
    final MyChangeListener changeListener = new MyChangeListener();
    myGrowSpinner.setModel(new SpinnerNumberModel(1.0, 0.0, 10.0, 0.1));
    myGrowSpinner.addChangeListener(changeListener);
    myMinSizeSpinner.addChangeListener(changeListener);
    myMaxSizeSpinner.addChangeListener(changeListener);
    myConstantSizeSpinner.addChangeListener(changeListener);
    myLeftRadioButton.addChangeListener(changeListener);
    myCenterRadioButton.addChangeListener(changeListener);
    myRightRadioButton.addChangeListener(changeListener);
    myFillRadioButton.addChangeListener(changeListener);

    final MyItemListener itemListener = new MyItemListener();
    myMinSizeUnitsCombo.addItemListener(itemListener);
    myMaxSizeUnitsCombo.addItemListener(itemListener);
    myConstantSizeUnitsCombo.addItemListener(itemListener);
  }

  @Override
  public JPanel getComponent() {
    return myRootPanel;
  }

  @Override
  public void addChangeListener(ChangeListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void removeChangeListener(ChangeListener listener) {
    myListeners.remove(listener);
  }

  public void showProperties(final RadContainer container, final boolean row, final int[] selectedIndices) {
    if (mySaving) return;
    if (selectedIndices.length == 1) {
      showControls(true);
      myShowing = true;
      try {
        myLayout = (FormLayout)container.getLayout();
        myIndex = selectedIndices[0] + 1;
        myIsRow = row;

        myTitleLabel.setText(myIsRow
                             ? UIDesignerBundle.message("title.row.properties", myIndex)
                             : UIDesignerBundle.message("title.column.properties", myIndex));
        myLeftRadioButton.setText(row ? UIDesignerBundle.message("alignment.top") : UIDesignerBundle.message("alignment.left"));
        myRightRadioButton.setText(row ? UIDesignerBundle.message("alignment.bottom") : UIDesignerBundle.message("alignment.right"));
        mySizePanel.setBorder(IdeBorderFactory.createTitledBorder(myIsRow
                                                                  ? UIDesignerBundle.message("title.height")
                                                                  : UIDesignerBundle.message("title.width")));

        FormSpec formSpec = row ? myLayout.getRowSpec(myIndex) : myLayout.getColumnSpec(myIndex);
        showAlignment(formSpec.getDefaultAlignment());
        showSize(formSpec.getSize());
        if (formSpec.getResizeWeight() < 0.01) {
          myGrowCheckBox.setSelected(false);
          myGrowSpinner.setValue(1.0);
        }
        else {
          myGrowCheckBox.setSelected(true);
          myGrowSpinner.setValue(formSpec.getResizeWeight());
        }
      }
      finally {
        myShowing = false;
      }
    }
    else {
      showControls(false);
      if (selectedIndices.length > 1) {
        myTitleLabel.setText(myIsRow
                             ? UIDesignerBundle.message("title.multiple.rows.selected")
                             : UIDesignerBundle.message("title.multiple.columns.selected"));
      }
      else {
        myTitleLabel.setText(myIsRow
                             ? UIDesignerBundle.message("title.no.rows.selected")
                             : UIDesignerBundle.message("title.no.columns.selected"));
      }
    }
  }

  private void showControls(final boolean visible) {
    mySizePanel.setVisible(visible);
    myAlignmentPanel.setVisible(visible);
    myGrowCheckBox.setVisible(visible);
    myGrowSpinner.setVisible(visible);
  }

  private void showAlignment(final FormSpec.DefaultAlignment defaultAlignment) {
    if (defaultAlignment.equals(RowSpec.TOP) || defaultAlignment.equals(ColumnSpec.LEFT)) {
      myLeftRadioButton.setSelected(true);
    }
    else if (defaultAlignment.equals(RowSpec.CENTER)) {
      myCenterRadioButton.setSelected(true);
    }
    else if (defaultAlignment.equals(RowSpec.BOTTOM) || defaultAlignment.equals(ColumnSpec.RIGHT)) {
      myRightRadioButton.setSelected(true);
    }
    else {
      myFillRadioButton.setSelected(true);
    }
  }

  private void showSize(Size size) {
    Size minimumSize = null;
    Size maximumSize = null;
    if (size instanceof BoundedSize boundedSize) {
      minimumSize = boundedSize.getLowerBound();
      maximumSize = boundedSize.getUpperBound();
      size = boundedSize.getBasis();
    }

    if (size instanceof ConstantSize) {
      myConstantRadioButton.setSelected(true);
      myMinimumCheckBox.setEnabled(false);
      myMinimumCheckBox.setSelected(false);
      myMaximumCheckBox.setEnabled(false);
      myMaximumCheckBox.setSelected(false);
      showConstantSize((ConstantSize)size, myConstantSizeUnitsCombo, myConstantSizeSpinner);
    }
    else {
      String s = size.toString();
      if (s.startsWith("m")) {
        myMinimumRadioButton.setSelected(true);
      }
      else if (s.startsWith("p")) {
        myPreferredRadioButton.setSelected(true);
      }
      else {
        myDefaultRadioButton.setSelected(true);
      }

      myMinimumCheckBox.setEnabled(true);
      myMaximumCheckBox.setEnabled(true);
      if (minimumSize instanceof ConstantSize) {
        myMinimumCheckBox.setSelected(true);
        myMaximumCheckBox.setEnabled(false);       // TODO: remove this code when IDEADEV-9678 is implemented
        showConstantSize((ConstantSize)minimumSize, myMinSizeUnitsCombo, myMinSizeSpinner);
      }
      else {
        myMinimumCheckBox.setSelected(false);
      }
      if (maximumSize instanceof ConstantSize) {
        myMaximumCheckBox.setSelected(true);
        myMinimumCheckBox.setEnabled(false);       // TODO: remove this code when IDEADEV-9678 is implemented
        showConstantSize((ConstantSize)maximumSize, myMaxSizeUnitsCombo, myMaxSizeSpinner);
      }
      else {
        myMaximumCheckBox.setSelected(false);
      }
    }
  }

  private static void showConstantSize(final ConstantSize size, final JComboBox unitsCombo, final JSpinner spinner) {
    double value = size.getValue();
    ConstantSize.Unit unit = size.getUnit();
    @NlsSafe String item;
    if (unit.equals(ConstantSize.DIALOG_UNITS_X) || unit.equals(ConstantSize.DIALOG_UNITS_Y)) {
      item = "dlu";
    }
    else {
      item = unit.abbreviation();
    }
    unitsCombo.setSelectedItem(item);

    spinner.setModel(new SpinnerNumberModel(0.0, 0.0, Double.MAX_VALUE, 1.0));
    spinner.setValue(value);
  }

  private void updateOnRadioChange() {
    final boolean canSetBounds = !myConstantRadioButton.isSelected();
    myMinimumCheckBox.setEnabled(canSetBounds);
    myMaximumCheckBox.setEnabled(canSetBounds);

    // TODO: remove this code when IDEADEV-9678 is implemented
    if (myMinimumCheckBox.isSelected()) {
      myMaximumCheckBox.setEnabled(false);
      myMaximumCheckBox.setSelected(false);
    }
    else if (myMaximumCheckBox.isSelected()) {
      myMinimumCheckBox.setEnabled(false);
    }

    if (!canSetBounds) {
      myMinimumCheckBox.setSelected(false);
      myMaximumCheckBox.setSelected(false);
    }
    updateSpec();
  }

  private void updateSpec() {
    if (myLayout == null || myShowing) return;
    mySaving = true;
    try {
      Size size = getSelectedSize();

      final SpinnerNumberModel model = (SpinnerNumberModel)myGrowSpinner.getModel();
      double resizeWeight = myGrowCheckBox.isSelected() ? model.getNumber().doubleValue() : 0.0;
      FormSpec.DefaultAlignment alignment = getSelectedAlignment();

      if (myIsRow) {
        myLayout.setRowSpec(myIndex, new RowSpec(alignment, size, resizeWeight));
      }
      else {
        myLayout.setColumnSpec(myIndex, new ColumnSpec(alignment, size, resizeWeight));
      }
      for (ChangeListener listener : myListeners) {
        listener.stateChanged(new ChangeEvent(this));
      }
    }
    finally {
      mySaving = false;
    }
  }

  private Size getSelectedSize() {
    Size size;
    if (myDefaultRadioButton.isSelected()) {
      size = Sizes.DEFAULT;
    }
    else if (myPreferredRadioButton.isSelected()) {
      size = Sizes.PREFERRED;
    }
    else if (myMinimumRadioButton.isSelected()) {
      size = Sizes.MINIMUM;
    }
    else {
      size = getConstantSize(myConstantSizeUnitsCombo, myConstantSizeSpinner);
    }

    if (myMinimumCheckBox.isSelected() || myMaximumCheckBox.isSelected()) {
      Size minSize = null;
      Size maxSize = null;
      if (myMinimumCheckBox.isSelected()) {
        minSize = getConstantSize(myMinSizeUnitsCombo, myMinSizeSpinner);
      }
      if (myMaximumCheckBox.isSelected()) {
        maxSize = getConstantSize(myMaxSizeUnitsCombo, myMaxSizeSpinner);
      }
      size = Sizes.bounded(size, minSize, maxSize);
    }
    return size;
  }

  private FormSpec.DefaultAlignment getSelectedAlignment() {
    if (myLeftRadioButton.isSelected()) {
      return myIsRow ? RowSpec.TOP : ColumnSpec.LEFT;
    }
    if (myCenterRadioButton.isSelected()) {
      return RowSpec.CENTER;
    }
    if (myRightRadioButton.isSelected()) {
      return myIsRow ? RowSpec.BOTTOM : ColumnSpec.RIGHT;
    }
    return RowSpec.FILL;
  }

  private ConstantSize getConstantSize(final JComboBox unitsCombo, final JSpinner spinner) {
    return Sizes.constant(spinner.getValue().toString() + Objects.requireNonNull(unitsCombo.getSelectedItem()).toString(), myIsRow);
  }

  private static class UnitListCellRenderer extends SimpleListCellRenderer<String> {
    @Override
    public void customize(@NotNull JList<? extends String> list, String value, int index, boolean selected, boolean hasFocus) {
      setText(UNITS_MAP.getOrDefault(value, ""));
    }
  }

  private class MyRadioListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent e) {
      updateOnRadioChange();
    }
  }

  private class MyCheckboxListener implements ChangeListener {
    private boolean myWasSelected;
    private final AbstractButton myButton;
    private final JComboBox myUnitsCombo;
    private final JSpinner mySpinner;

    MyCheckboxListener(final AbstractButton button, final JComboBox unitsCombo, final JSpinner spinner) {
      myButton = button;
      myUnitsCombo = unitsCombo;
      mySpinner = spinner;
      myWasSelected = myButton.isSelected();
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      if (myWasSelected != myButton.isSelected()) {
        myWasSelected = myButton.isSelected();
        myUnitsCombo.setEnabled(myButton.isSelected());
        mySpinner.setEnabled(myButton.isSelected());
        if (myButton.isSelected() && mySpinner.getValue().equals(Integer.valueOf(0))) {
          mySpinner.setValue(100);
        }
        updateOnRadioChange();
      }
    }
  }

  private class MyChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      updateSpec();
    }
  }

  private class MyItemListener implements ItemListener {
    @Override
    public void itemStateChanged(ItemEvent e) {
      updateSpec();
    }
  }
}