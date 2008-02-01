package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerTreePanel {
  private XDebuggerTree myTree;
  private JPanel myMainPanel;

  public XDebuggerTreePanel(XStackFrame stackFrame) {
    myTree = new XDebuggerTree(stackFrame);
    myMainPanel = new JPanel(new BorderLayout());
    myMainPanel.add(ScrollPaneFactory.createScrollPane(myTree), BorderLayout.CENTER);
  }

  public XDebuggerTree getTree() {
    return myTree;
  }

  public JPanel getMainPanel() {
    return myMainPanel;
  }
}
