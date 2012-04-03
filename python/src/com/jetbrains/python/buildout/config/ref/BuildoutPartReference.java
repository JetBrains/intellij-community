package com.jetbrains.python.buildout.config.ref;

import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.jetbrains.django.lang.template.psi.impl.DjangoTemplateFileImpl;
import com.jetbrains.django.model.TemplateManager;
import com.jetbrains.django.ref.BaseReference;
import com.jetbrains.python.PythonStringUtil;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgFile;
import com.jetbrains.python.buildout.config.psi.impl.BuildoutCfgSection;
import com.jetbrains.python.psi.PyElementGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author traff
 */
public class BuildoutPartReference extends BaseReference {
  private final String myPartName;
  private final int myOffsetInElement;

  public BuildoutPartReference(PsiElement element, String partName, int offsetInElement) {
    super(element);
    myPartName = partName;
    myOffsetInElement = offsetInElement;
  }

  @Override
  public TextRange getRangeInElement() {
    return TextRange.from(myOffsetInElement, myPartName.length());
  }

  public PsiElement resolve() {
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      return file.findSectionByName(myPartName);
    }
    return null;
  }

  @NotNull
  public Object[] getVariants() {
    List<String> res = Lists.newArrayList();
    BuildoutCfgFile file = PsiTreeUtil.getParentOfType(myElement, BuildoutCfgFile.class);
    if (file != null) {
      for (BuildoutCfgSection sec : file.getSections()) {
        String name = sec.getHeaderName();
        if (name != null) {
          res.add(name);
        }
      }
      return res.toArray();
    }
    return EMPTY_ARRAY;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) {
    String fullName = PythonStringUtil.replaceLastSuffix(getElement().getText(), "/", newElementName);
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
