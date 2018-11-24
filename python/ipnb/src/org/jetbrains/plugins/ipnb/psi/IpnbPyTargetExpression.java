package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.wm.impl.FocusManagerImpl;
import com.jetbrains.python.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;

public class IpnbPyTargetExpression extends PyTargetExpressionImpl {

  public IpnbPyTargetExpression(ASTNode astNode) {
    super(astNode);
  }

  public IpnbPyTargetExpression(final PyTargetExpressionStub stub) {
    super(stub, IpnbPyTokenTypes.IPNB_TARGET);
  }

  @Override
  public void navigate(boolean requestFocus) {
    final IpnbCodeSourcePanel sourcePanel = ((IpnbPyFragment)getContainingFile()).getCodeSourcePanel();
    final Editor editor = sourcePanel.getEditor();

    final IpnbCodePanel codePanel = sourcePanel.getIpnbCodePanel();
    final IpnbFileEditor fileEditor = codePanel.getFileEditor();
    final IpnbFilePanel filePanel = fileEditor.getIpnbFilePanel();
    codePanel.setEditing(true);
    filePanel.setSelectedCellPanel(codePanel);
    super.navigate(false);
    FocusManagerImpl.getInstance().requestFocus(editor.getContentComponent(), true);
  }

  @Nullable
  @Override
  public PyTargetExpressionStub getStub() {
    return null;
  }
}

