package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.ui.ScrollPaneFactory;

import javax.swing.*;
import java.awt.*;

/**
 * @author nik
 */
public class XDebuggerTreePanel {
  private XDebuggerTree myTree;
  private JPanel myMainPanel;

  public XDebuggerTreePanel() {
    myTree = new XDebuggerTree();
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
