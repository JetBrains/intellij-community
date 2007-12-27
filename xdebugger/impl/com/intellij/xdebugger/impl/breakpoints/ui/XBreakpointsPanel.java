package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointManager;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.breakpoints.ui.actions.GoToBreakpointAction;
import com.intellij.xdebugger.impl.breakpoints.ui.actions.RemoveBreakpointAction;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public class XBreakpointsPanel<B extends XBreakpoint<?>> extends AbstractBreakpointPanel<XBreakpoint> {
  private final Project myProject;
  private final DialogWrapper myParentDialog;
  private final XBreakpointType<B, ?> myType;
  private JPanel myMainPanel;
  private JPanel myPropertiesPanelWrapper;
  private JPanel myTreePanel;
  private JPanel myPropertiesPanel;
  private JPanel myButtonsPanel;
  private XBreakpointsTree<B> myTree;
  private XBreakpointPanelAction<B>[] myActions;
  private Map<XBreakpointPanelAction<B>, JButton> myButtons;

  public XBreakpointsPanel(@NotNull Project project, @NotNull DialogWrapper parentDialog, @NotNull XBreakpointType<B, ?> type) {
    super(type.getTitle(), null, XBreakpoint.class);
    myProject = project;
    myParentDialog = parentDialog;
    myType = type;
    myTree = XBreakpointsTree.createTree();
    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(final TreeSelectionEvent e) {
        onSelectionChanged();
      }
    });

    //noinspection unchecked
    myActions = new XBreakpointPanelAction[] {
      new GoToBreakpointAction<B>(this, XDebuggerBundle.message("xbreakpoints.dialog.button.goto"), true),
      new GoToBreakpointAction<B>(this, XDebuggerBundle.message("xbreakpoints.dialog.button.view.source"), false),
      new RemoveBreakpointAction<B>(this)
    };

    myTreePanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
    initButtons();
  }

  private void onSelectionChanged() {
    List<B> breakpoints = myTree.getSelectedBreakpoints();
    for (XBreakpointPanelAction<B> action : myActions) {
      JButton button = myButtons.get(action);
      button.setEnabled(action.isEnabled(breakpoints));
    }
  }

  private void initButtons() {
    myButtons = new HashMap<XBreakpointPanelAction<B>, JButton>();
    for (final XBreakpointPanelAction<B> action : myActions) {
      JButton button = createButton(action);
      GridBagConstraints constraints = new GridBagConstraints();
      constraints.gridx = 0;
      constraints.fill = GridBagConstraints.HORIZONTAL;
      constraints.insets = new Insets(0, 2, 2, 2);
      myButtonsPanel.add(button, constraints);
      myButtons.put(action, button);
    }
  }

  private JButton createButton(final XBreakpointPanelAction<B> action) {
    JButton button = new JButton(action.getName());
    button.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        List<B> list = myTree.getSelectedBreakpoints();
        action.perform(list);
      }
    });
    return button;
  }

  public void dispose() {
  }

  public Icon getTabIcon() {
    for (B b : getBreakpoints()) {
      if (b.isEnabled()) {
        return myType.getEnabledIcon();
      }
    }
    return myType.getDisabledIcon();
  }

  public void saveBreakpoints() {
  }

  public XBreakpointManager getBreakpointManager() {
    return XDebuggerManager.getInstance(myProject).getBreakpointManager();
  }

  public void resetBreakpoints() {
    Collection<? extends B> breakpoints = getBreakpoints();
    myTree.buildTree(breakpoints);
    fireBreakpointsChanged();
  }

  private Collection<? extends B> getBreakpoints() {
    return getBreakpointManager().getBreakpoints(myType);
  }

  public JPanel getPanel() {
    return myMainPanel;
  }

  public boolean canSelectBreakpoint(final XBreakpoint breakpoint) {
    return breakpoint.getType().equals(myType);
  }

  public void selectBreakpoint(final XBreakpoint breakpoint) {
  }

  public DialogWrapper getParentDialog() {
    return myParentDialog;
  }
}
