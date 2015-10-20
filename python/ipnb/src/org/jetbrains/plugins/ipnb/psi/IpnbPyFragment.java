package org.jetbrains.plugins.ipnb.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.testFramework.LightVirtualFile;
import com.jetbrains.python.psi.impl.PyFileImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.ipnb.editor.panels.IpnbFilePanel;
import org.jetbrains.plugins.ipnb.editor.panels.code.IpnbCodeSourcePanel;

public class IpnbPyFragment extends PyFileImpl {
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

  @NotNull
  public FileViewProvider getViewProvider() {
    if(myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  public boolean isPhysical() {
    return myPhysical;
  }

  public IpnbFilePanel getFilePanel() {
    return myFilePanel;
  }

  @Nullable
  @Override
  public PsiDirectory getContainingDirectory() {
    final VirtualFile file = myFilePanel.getVirtualFile();
    final VirtualFile parentFile = file.getParent();
    if (parentFile == null) {
      return super.getContainingDirectory();
    }
    if (!parentFile.isValid()) {
      return super.getContainingDirectory();
    }
    return getManager().findDirectory(parentFile);
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }
}