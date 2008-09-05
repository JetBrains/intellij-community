package org.jetbrains.idea.svn;

import org.tmatesoft.svn.core.SVNDepth;

import javax.swing.*;

public class DepthCombo extends JComboBox {
  public DepthCombo() {
    super(new SVNDepth[] {SVNDepth.EMPTY, SVNDepth.FILES, SVNDepth.IMMEDIATES, SVNDepth.INFINITY});
    setSelectedIndex(3);
    setEditable(false);
    setToolTipText(SvnBundle.message("label.depth.description"));
  }

  @Override
  public SVNDepth getSelectedItem() {
    return (SVNDepth) super.getSelectedItem();
  }
}
