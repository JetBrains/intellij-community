package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;

public class IpnbPyTargetExpression extends PyTargetExpressionImpl {

  public IpnbPyTargetExpression(ASTNode astNode) {
    super(astNode);
  }

  @Override
  public void navigate(boolean requestFocus) {
    final IpnbCodeSourcePanel sourcePanel = ((IpnbPyFragment)getContainingFile()).getCodeSourcePanel();
    final Editor editor = sourcePanel.getEditor();

    final IpnbCodePanel codePanel = sourcePanel.getIpnbCodePanel();
    final IpnbFileEditor fileEditor = codePanel.getFileEditor();
    final IpnbFilePanel filePanel = fileEditor.getIpnbFilePanel();
    codePanel.setEditing(true);
    filePanel.setSelectedCell(codePanel);
    super.navigate(false);
    UIUtil.requestFocus(editor.getContentComponent());

  }

}

