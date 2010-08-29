package com.jetbrains.python.buildout.config.ref;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.django.lang.template.psi.impl.DjangoTemplateFileImpl;
import com.jetbrains.django.model.TemplateManager;
import com.jetbrains.django.ref.BaseReference;
import com.jetbrains.django.util.DjangoStringUtil;
import com.jetbrains.django.util.PythonUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class BuildoutPartReference extends BaseReference {
  private final String myPartName;

  public BuildoutPartReference(PsiElement element, String partName) {
    super(element);
    myPartName = partName;
  }

  @Override
  public TextRange getRangeInElement() {
    int ind = myElement.getText().indexOf(myPartName);
    return TextRange.from(ind, myPartName.length());
  }

  public PsiElement resolve() {
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      BuildoutCfgSection section = file.findSectionByName(myPartName);
      return section;
    }
    return null;
  }

  @NotNull
  public Object[] getVariants() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) {
    String fullName = DjangoStringUtil.replaceLastSuffix(getElement().getText(), "/", newElementName);
    return myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralAlreadyEscaped(fullName));
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    Module module = ModuleUtil.findModuleForPsiElement(myElement);
    if (module != null) {
      String name = TemplateManager.getRelativeName(module, (DjangoTemplateFileImpl)element);
      if (name != null) {
        return myElement.replace(PyElementGenerator.getInstance(myElement.getProject()).createStringLiteralFromString(name));
      }
    }
    return myElement;
  }
}
