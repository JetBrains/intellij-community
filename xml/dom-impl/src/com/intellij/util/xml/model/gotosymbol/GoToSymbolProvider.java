// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.util.xml.model.gotosymbol;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.ElementPresentationManager;
import com.intellij.util.xml.GenericDomValue;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import java.util.*;

/**
 * Base class for "Go To Symbol" contributors.
 */
public abstract class GoToSymbolProvider implements ChooseByNameContributor {
  // non-static to store modules accepted by different providers separately
  private final Key<CachedValue<Collection<Module>>> ACCEPTABLE_MODULES = Key.create("ACCEPTABLE_MODULES_" + this);

  protected abstract void addNames(@NotNull Module module, Set<String> result);

  protected abstract void addItems(@NotNull Module module, String name, List<NavigationItem> result);

  protected abstract boolean acceptModule(Module module);

  private Collection<Module> getAcceptableModules(Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, ACCEPTABLE_MODULES, () ->
      CachedValueProvider.Result.create(calcAcceptableModules(project), PsiModificationTracker.MODIFICATION_COUNT), false);
  }

  protected @Unmodifiable @NotNull Collection<Module> calcAcceptableModules(@NotNull Project project) {
    return ContainerUtil.findAll(ModuleManager.getInstance(project).getModules(), module -> acceptModule(module));
  }

  @Override
  public String @NotNull [] getNames(Project project, boolean includeNonProjectItems) {
    Set<String> result = new HashSet<>();
    for (Module module : getAcceptableModules(project)) {
      addNames(module, result);
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public NavigationItem @NotNull [] getItemsByName(String name,
                                                   String pattern,
                                                   Project project,
                                                   boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<>();
    for (Module module : getAcceptableModules(project)) {
      addItems(module, name, result);
    }
    return result.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }

  protected static @Nullable NavigationItem createNavigationItem(DomElement domElement) {
    GenericDomValue name = domElement.getGenericInfo().getNameDomElement(domElement);
    assert name != null;
    XmlElement psiElement = name.getXmlElement();
    String value = name.getStringValue();
    if (psiElement == null || value == null) {
      return null;
    }
    Icon icon = ElementPresentationManager.getIcon(domElement);
    return createNavigationItem(psiElement, value, icon);
  }

  public static @NotNull NavigationItem createNavigationItem(@NotNull PsiElement element,
                                                             @NotNull @NonNls String text,
                                                             @Nullable Icon icon) {
    return new BaseNavigationItem(element, text, icon);
  }


  /**
   * Wraps one entry to display in "Go To Symbol" dialog.
   */
  public static class BaseNavigationItem extends FakePsiElement {

    private final PsiElement myPsiElement;
    private final String myText;
    private final Icon myIcon;

    /**
     * Creates a new display item.
     *
     * @param psiElement The PsiElement to navigate to.
     * @param text       Text to show for this element.
     * @param icon       Icon to show for this element.
     */
    public BaseNavigationItem(@NotNull PsiElement psiElement, @NotNull @NonNls String text, @Nullable Icon icon) {
      myPsiElement = psiElement;
      myText = text;
      myIcon = icon;
    }

    @Override
    public @NotNull PsiElement getNavigationElement() {
      return myPsiElement;
    }

    @Override
    public Icon getIcon(boolean flags) {
      return myIcon;
    }

    @Override
    public String getName() {
      return myText;
    }

    @Override
    public ItemPresentation getPresentation() {
      return new ItemPresentation() {

        @Override
        public String getPresentableText() {
          return myText;
        }

        @Override
        public String getLocationString() {
          return ReadAction.nonBlocking(() -> '(' + myPsiElement.getContainingFile().getName() + ')').executeSynchronously();
        }

        @Override
        public @Nullable Icon getIcon(boolean open) {
          return myIcon;
        }
      };
    }

    @Override
    public PsiElement getParent() {
      return myPsiElement.getParent();
    }

    @Override
    public @NotNull Project getProject() {
      return myPsiElement.getProject();
    }

    @Override
    public PsiFile getContainingFile() {
      return myPsiElement.getContainingFile();
    }

    @Override
    public boolean isValid() {
      return myPsiElement.isValid();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BaseNavigationItem that = (BaseNavigationItem)o;

      if (!myPsiElement.equals(that.myPsiElement)) return false;
      if (!myText.equals(that.myText)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result;
      result = myPsiElement.hashCode();
      result = 31 * result + myText.hashCode();
      return result;
    }
  }
}