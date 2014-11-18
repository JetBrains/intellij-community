package org.jetbrains.plugins.ipnb.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.python.psi.impl.PyFunctionImpl;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import org.jetbrains.plugins.ipnb.editor.IpnbFileEditor;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;

public class IpnbPyFunction extends PyFunctionImpl {

  public IpnbPyFunction(ASTNode astNode) {
    super(astNode);
  }

  public IpnbPyFunction(PyFunctionStub stub) {
    super(stub);
  }

  public IpnbPyFunction(PyFunctionStub stub, IStubElementType nodeType) {
    super(stub, nodeType);
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

