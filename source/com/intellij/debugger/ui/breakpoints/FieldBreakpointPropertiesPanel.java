/**
 * class FieldBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiElement;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FieldBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  private JCheckBox myWatchAccessCheckBox;
  private JCheckBox myWatchModificationCheckBox;

  public FieldBreakpointPropertiesPanel(final Project project) {
    super(project);
  }

  protected JComponent createSpecialBox() {
    JPanel _panel;
    JPanel _panel0;
    myWatchAccessCheckBox = new JCheckBox("Field access");
    myWatchAccessCheckBox.setMnemonic('s');
    myWatchModificationCheckBox = new JCheckBox("Field modification");
    myWatchModificationCheckBox.setMnemonic('m');


    Box watchBox = Box.createVerticalBox();
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchAccessCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchModificationCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);

    _panel = new JPanel(new BorderLayout());
    _panel0 = new JPanel(new BorderLayout());
    _panel0.add(watchBox, BorderLayout.CENTER);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    _panel.add(_panel0, BorderLayout.NORTH);
    _panel.setBorder(IdeBorderFactory.createTitledBorder("Watch"));

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        JCheckBox toCheck = null;
        if (!myWatchAccessCheckBox.isSelected() && !myWatchModificationCheckBox.isSelected()) {
          Object source = e.getSource();
          if (myWatchAccessCheckBox.equals(source)) {
            toCheck = myWatchModificationCheckBox;
          }
          else if (myWatchModificationCheckBox.equals(source)) {
            toCheck = myWatchAccessCheckBox;
          }
          if (toCheck != null) {
            toCheck.setSelected(true);
          }
        }
      }
    };
    myWatchAccessCheckBox.addActionListener(listener);
    myWatchModificationCheckBox.addActionListener(listener);

    return _panel;
  }

  public void initFrom(Breakpoint breakpoint) {
    super.initFrom(breakpoint);
    FieldBreakpoint fieldBreakpoint = (FieldBreakpoint)breakpoint;

    myWatchAccessCheckBox.setSelected(fieldBreakpoint.WATCH_ACCESS);
    myWatchModificationCheckBox.setSelected(fieldBreakpoint.WATCH_MODIFICATION);
  }

  public void saveTo(Breakpoint breakpoint, Runnable afterUpdate) {
    FieldBreakpoint fieldBreakpoint = (FieldBreakpoint)breakpoint;

    fieldBreakpoint.WATCH_ACCESS = myWatchAccessCheckBox.isSelected();
    fieldBreakpoint.WATCH_MODIFICATION = myWatchModificationCheckBox.isSelected();
    
    super.saveTo(breakpoint, afterUpdate);
  }
}