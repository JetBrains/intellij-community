package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.AbstractFindUsagesDialog;
import com.intellij.find.findUsages.CommonFindUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyModuleFindUsagesHandler extends FindUsagesHandler {
  private final PsiFileSystemItem myElement;

  protected PyModuleFindUsagesHandler(@NotNull PyFile file) {
    super(file);
    final PsiElement e = PyUtil.turnInitIntoDir(file);
    myElement = e instanceof PsiFileSystemItem ? (PsiFileSystemItem)e : file;
  }

  @NotNull
  @Override
  public PsiElement[] getPrimaryElements() {
    return new PsiElement[] {myElement};
  }

  @NotNull
  @Override
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myElement,
                                      getProject(),
                                      getFindUsagesOptions(),
                                      toShowInNewTab,
                                      mustOpenInNewTab,
                                      isSingleFile,
                                      this) {
      @Override
      public void configureLabelComponent(final SimpleColoredComponent coloredComponent) {
        coloredComponent.append(myElement instanceof PsiDirectory ? "Package " : "Module ");
        coloredComponent.append(myElement.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    };
  }
}
