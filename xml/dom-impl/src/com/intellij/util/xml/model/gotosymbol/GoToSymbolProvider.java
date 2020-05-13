// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml.model.gotosymbol;

import com.intellij.navigation.ChooseByNameContributor;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
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

import javax.swing.*;
import java.util.*;

/**
 * Base class for "Go To Symbol" contributors.
 */
public abstract class GoToSymbolProvider implements ChooseByNameContributor {
  // non-static to store modules accepted by different providers separately
  private final Key<CachedValue<Collection<Module>>> ACCEPTABLE_MODULES = Key.create("ACCEPTABLE_MODULES_" + toString());

  protected abstract void addNames(@NotNull Module module, Set<String> result);

  protected abstract void addItems(@NotNull Module module, String name, List<NavigationItem> result);

  protected abstract boolean acceptModule(final Module module);

  protected static void addNewNames(@NotNull final List<? extends DomElement> elements, final Set<? super String> existingNames) {
    for (DomElement name : elements) {
      existingNames.add(name.getGenericInfo().getElementName(name));
    }
  }

  private Collection<Module> getAcceptableModules(final Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, ACCEPTABLE_MODULES, () ->
      CachedValueProvider.Result.create(calcAcceptableModules(project), PsiModificationTracker.MODIFICATION_COUNT), false);
  }

  @NotNull
  protected Collection<Module> calcAcceptableModules(@NotNull Project project) {
    return ContainerUtil.findAll(ModuleManager.getInstance(project).getModules(), module -> acceptModule(module));
  }

  @Override
  public String @NotNull [] getNames(final Project project, boolean includeNonProjectItems) {
    Set<String> result = new HashSet<>();
    for (Module module : getAcceptableModules(project)) {
      addNames(module, result);
    }
    return ArrayUtilRt.toStringArray(result);
  }

  @Override
  public NavigationItem @NotNull [] getItemsByName(final String name, final String pattern, final Project project, boolean includeNonProjectItems) {
    List<NavigationItem> result = new ArrayList<>();
    for (Module module : getAcceptableModules(project)) {
      addItems(module, name, result);
    }
    return result.toArray(NavigationItem.EMPTY_NAVIGATION_ITEM_ARRAY);
  }

  @Nullable
  protected static NavigationItem createNavigationItem(final DomElement domElement) {
    final GenericDomValue name = domElement.getGenericInfo().getNameDomElement(domElement);
    assert name != null;
    final XmlElement psiElement = name.getXmlElement();
    final String value = name.getStringValue();
    if (psiElement == null || value == null) {
      return null;
    }
    final Icon icon = ElementPresentationManager.getIcon(domElement);
    return createNavigationItem(psiElement, value, icon);
  }

  @NotNull
  protected static NavigationItem createNavigationItem(@NotNull final PsiElement element,
                                                       @NotNull @NonNls final String text,
                                                       @Nullable final Icon icon) {
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
    @NotNull
    public PsiElement getNavigationElement() {
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
          return '(' + myPsiElement.getContainingFile().getName() + ')';
        }

        @Override
        @Nullable
        public Icon getIcon(boolean open) {
          return myIcon;
        }
      };
    }

    @Override
    public PsiElement getParent() {
      return myPsiElement.getParent();
    }

    @NotNull
    @Override
    public Project getProject() {
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

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final BaseNavigationItem that = (BaseNavigationItem)o;

      if (!myPsiElement.equals(that.myPsiElement)) return false;
      if (!myText.equals(that.myText)) return false;

      return true;
    }

    public int hashCode() {
      int result;
      result = myPsiElement.hashCode();
      result = 31 * result + myText.hashCode();
      return result;
    }
  }

}