package com.jetbrains.python.psi.resolve;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.python.PyNames;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class QualifiedNameResolveContext {
  @Nullable private Module myModule;
  private PsiFile myFootholdFile;
  @NotNull PsiManager myPsiManager;
  private Sdk mySdk;

  public void copyFrom(QualifiedNameResolveContext context) {
    myModule = context.getModule();
    myPsiManager = context.getPsiManager();
    mySdk = context.getSdk();
    myFootholdFile = context.getFootholdFile();
  }

  public void setFromElement(PsiElement foothold) {
    if (foothold instanceof PsiDirectory) {
      myFootholdFile = ((PsiDirectory)foothold).findFile(PyNames.INIT_DOT_PY);
    }
    else {
      myFootholdFile = foothold.getContainingFile().getOriginalFile();
    }
    myPsiManager = foothold.getManager();
    myModule = ModuleUtilCore.findModuleForPsiElement(foothold);
  }

  public void setFromModule(Module module) {
    myModule = module;
    myPsiManager = PsiManager.getInstance(module.getProject());
  }

  public void setFromSdk(Project project, Sdk sdk) {
    myPsiManager = PsiManager.getInstance(project);
    mySdk = sdk;
  }

  public void setSdk(Sdk sdk) {
    mySdk = sdk;
  }

  @Nullable
  public Module getModule() {
    return myModule;
  }

  public boolean isValid() {
    if (myFootholdFile != null) {
      return myFootholdFile.isValid();
    }
    return true;
  }

  @Nullable
  public PsiFile getFootholdFile() {
    return myFootholdFile;
  }

  @NotNull
  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @NotNull
  public Project getProject() {
    return myPsiManager.getProject();
  }

  public Sdk getSdk() {
    return mySdk;
  }
}
