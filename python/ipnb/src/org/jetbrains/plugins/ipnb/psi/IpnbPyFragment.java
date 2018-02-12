package org.jetbrains.plugins.ipnb.psi;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.impl.FileManager;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.stubs.StubElement;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.indexing.IndexingDataKeys;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyUtil;
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
    super(PsiManagerEx.getInstanceEx(project).getFileManager().createFileViewProvider(
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
    SingleRootFileViewProvider cloneViewProvider =
      (SingleRootFileViewProvider)fileManager.createFileViewProvider(new LightVirtualFile(getName(), getLanguage(), getText()), false);
    cloneViewProvider.forceCachedPsi(clone);
    clone.myViewProvider = cloneViewProvider;
    return clone;
  }

  @Override
  @NotNull
  public FileViewProvider getViewProvider() {
    if (myViewProvider != null) return myViewProvider;
    return super.getViewProvider();
  }

  @Override
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

  @NotNull
  @Override
  public PsiFile getContext() {
    final PsiFile psiFile = PsiDocumentManager.getInstance(getProject()).getPsiFile(myFilePanel.getDocument());
    return psiFile != null ? psiFile : super.getOriginalFile();
  }

  @Override
  public PsiElement getNextSibling() {
    return null;
  }

  @Override
  public PsiElement getPrevSibling() {
    return null;
  }

  @Override
  public LanguageLevel getLanguageLevel() {
    VirtualFile virtualFile = getVirtualFile();

    if (virtualFile == null) {
      virtualFile = getUserData(IndexingDataKeys.VIRTUAL_FILE);
    }
    if (virtualFile == null) {
      virtualFile = getViewProvider().getVirtualFile();
    }
    return PyUtil.getLanguageLevelForVirtualFile(getProject(), virtualFile);
  }

  @Nullable
  @Override
  public StubElement getStub() {
    return null;
  }
}