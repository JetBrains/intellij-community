package com.intellij.debugger.settings;

import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.ui.impl.watch.render.ArrayRenderer;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class ArrayRendererConfigurable implements UnnamedConfigurable{
  private JTextField myEntriesLimit;
  private JTextField myFirstIndex;
  private JTextField myEndIndex;

  private ArrayRenderer myRenderer;
  private JComponent myPanel;

  private JLabel myEntriesLimitLabel;
  private JLabel myLastIndexLabel;
  private JLabel myFirstIndexLabel;

  public ArrayRendererConfigurable(ArrayRenderer renderer) {
    myRenderer = renderer;

    myFirstIndexLabel  .setLabelFor(myFirstIndex);
    myLastIndexLabel   .setLabelFor(myLastIndexLabel);
    myEntriesLimitLabel.setLabelFor(myEntriesLimit);

    DocumentListener listener = new DocumentListener() {
            private void updateEntriesLimit() {
              myEntriesLimit.setText(String.valueOf(getInt(myEndIndex) - getInt(myFirstIndex) + 1));
            }

            public void changedUpdate(DocumentEvent e) { updateEntriesLimit(); }
            public void insertUpdate (DocumentEvent e) { updateEntriesLimit(); }
            public void removeUpdate (DocumentEvent e) { updateEntriesLimit(); }
          };
    myFirstIndex.getDocument().addDocumentListener(listener);
    myEndIndex.getDocument().addDocumentListener(listener);
  }

  public ArrayRenderer getRenderer() {
    return myRenderer;
  }

  public void reset() {
    myFirstIndex  .setText(String.valueOf(myRenderer.START_INDEX));
    myEndIndex    .setText(String.valueOf(myRenderer.END_INDEX));
    myEntriesLimit.setText(String.valueOf(myRenderer.ENTRIES_LIMIT));
  }

  public void apply() {
    applyTo(myRenderer);
  }

  private void applyTo(ArrayRenderer renderer) {
    int newStartIndex = getInt(myFirstIndex);
    int newEndIndex   = getInt(myEndIndex);
    int newLimit      = getInt(myEntriesLimit);

    if (newStartIndex >= 0 && newEndIndex >= 0) {
      if (newStartIndex >= newEndIndex) {
        int currentStartIndex = renderer.START_INDEX;
        int currentEndIndex = renderer.END_INDEX;
        newEndIndex = newStartIndex + (currentEndIndex - currentStartIndex);
      }

      if(newLimit <= 0) newLimit = 1;

      if(newEndIndex - newStartIndex > 10000) {
        if(Messages.showOkCancelDialog(myPanel.getRootPane(), "Range specified is too big. IDEA needs too much resources to perform requested operation. Are you shure you want to continue?", "Range is Too Big", Messages.getWarningIcon()) != DialogWrapper.OK_EXIT_CODE) return;
      }
    }

    renderer.START_INDEX   = newStartIndex;
    renderer.END_INDEX     = newEndIndex;
    renderer.ENTRIES_LIMIT = newLimit;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  private int getInt(JTextField textField) {
    int newEndIndex = 0;
    try {
      newEndIndex = Integer.parseInt(textField.getText().trim());
    }
    catch (NumberFormatException exception) {
    }
    return newEndIndex;
  }

  public boolean isModified() {
    ArrayRenderer cloneRenderer = myRenderer.clone();
    applyTo(cloneRenderer);
    return !DebuggerUtilsEx.externalizableEqual(myRenderer, cloneRenderer);
  }

  public void disposeUIResources() {
  }
}
