package org.jetbrains.plugins.ipnb.psi;

import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;

public class IpnbPyFragment extends PyFileImpl {
  private PsiElement myContext;
  private boolean myPhysical;
  private final IpnbFilePanel myFilePanel;
  private final IpnbCodeSourcePanel myCodeSourcePanel;
  private FileViewProvider myViewProvider;

  public IpnbPyFragment(Project project, CharSequence text, boolean isPhysical, IpnbCodeSourcePanel codeSourcePanel) {
    super(((PsiManagerEx)PsiManager.getInstance(project)).getFileManager().createFileViewProvider(
            new LightVirtualFile("code.py", IpnbPyLanguageDialect.getInstance(), text), isPhysical)
    );
    myPhysical = isPhysical;
    myCodeSourcePanel = codeSourcePanel;
    myFilePanel = codeSourcePanel.getIpnbCodePanel().getFileEditor().getIpnbFilePanel();
    ((SingleRootFileViewProvider)getViewProvider()).forceCachedPsi(this);
  }

  public IpnbCodeSourcePanel getCodeSourcePanel() {
    return myCodeSourcePanel;
  }

  protected IpnbPyFragment clone() {
    final IpnbPyFragment clone = (IpnbPyFragment)cloneImpl((FileElement)calcTreeElement().clone());
    clone.myPhysical = false;
    clone.myOriginalFile = this;
    FileManager fileManager = ((PsiManagerEx)getManager()).getFileManager();
    SingleRootFileViewProvider cloneViewProvider = (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(getName(), getLanguage(), getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  public PsiElement getContext() {
    return myContext;
  }

  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  public boolean isValid() {
    if (!super.isValid()) return false;
    if (myContext != null && !myContext.isValid()) return false;
    return true;
  }

  public boolean isPhysical() {
    return myPhysical;
  }

  public void setContext(PsiElement context) {
    myContext = context;
  }

  public IpnbFilePanel getFilePanel() {
    return myFilePanel;
  }
}