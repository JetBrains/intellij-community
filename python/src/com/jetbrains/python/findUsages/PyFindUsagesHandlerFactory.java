/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.findUsages;

import com.intellij.find.findUsages.*;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyFindUsagesHandlerFactory extends FindUsagesHandlerFactory implements PyPsiFindUsagesHandlerFactory {

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return PyPsiFindUsagesHandlerFactory.super.canFindUsages(element);
  }

  private static FindUsagesHandler proxy(final FindUsagesHandlerBase base) {
    if (base instanceof FindUsagesHandler) {
      return (FindUsagesHandler)base;
    }
    else if (base instanceof PyFindUsagesHandler) {
      return new FindUsagesHandler(base.getPsiElement()) {
        @Override
        public @NotNull FindUsagesOptions getFindUsagesOptions() {
          return base.getFindUsagesOptions();
        }

        @Override
        protected boolean isSearchForTextOccurrencesAvailable(@NotNull PsiElement psiElement, boolean isSingleFile) {
          return ((PyFindUsagesHandler)base).isSearchForTextOccurrencesAvailable(psiElement, isSingleFile);
        }

        @Override
        public PsiElement @NotNull [] getPrimaryElements() {
          return base.getPrimaryElements();
        }
      };
    }
    else {
      @NonNls String msg = base.toString() + " is of unexpected type.";
      throw new IllegalArgumentException(msg);
    }
  }

  @Override
  public @Nullable FindUsagesHandler createFindUsagesHandler(@NotNull PsiElement element, boolean forHighlightUsages) {
    return proxy(PyPsiFindUsagesHandlerFactory.super.createFindUsagesHandler(element, forHighlightUsages));
  }

  @Override
  public @NotNull PyModuleFindUsagesHandler createModuleFindUsagesHandler(@NotNull PsiFileSystemItem element) {
    return new PyModuleFindUsagesHandlerUi(element);
  }

  static class PyModuleFindUsagesHandlerUi extends PyModuleFindUsagesHandler implements FindUsagesHandlerUi {
    protected PyModuleFindUsagesHandlerUi(@NotNull PsiFileSystemItem file) {
      super(file);
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
        public void configureLabelComponent(@NonNls @NotNull final SimpleColoredComponent coloredComponent) {
          coloredComponent.append(myElement instanceof PsiDirectory ? "Package " : "Module ");
          coloredComponent.append(myElement.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
        }
      };
    }
  }
}
