package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.AbstractFindUsagesDialog;
import com.intellij.find.findUsages.CommonFindUsagesDialog;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyModuleFindUsagesHandler extends FindUsagesHandler {
  private final PyFile myFile;

  protected PyModuleFindUsagesHandler(@NotNull PyFile psiElement) {
    super(psiElement);
    myFile = psiElement;
  }

  @NotNull
  @Override
  public PsiElement[] getPrimaryElements() {
    final PsiDirectory dir = myFile.getContainingDirectory();
    if (dir == null) {
      return super.getPrimaryElements();
    }
    return new PsiElement[] { dir };
  }

  @NotNull
  @Override
  public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
    return new CommonFindUsagesDialog(myFile.getContainingDirectory(),
                                      getProject(),
                                      getFindUsagesOptions(),
                                      toShowInNewTab,
                                      mustOpenInNewTab,
                                      isSingleFile,
                                      this) {
      @Override
      public void configureLabelComponent(final SimpleColoredComponent coloredComponent) {
        final PsiDirectory dir = myFile.getContainingDirectory();
        if (dir == null) {
          super.configureLabelComponent(coloredComponent);
        }
        else {
          coloredComponent.append("Module ");
          coloredComponent.append(dir.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      }
    };
  }
}
