// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.yaml.structureView;

import com.intellij.ide.structureView.StructureViewTreeElement;
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase;
import com.intellij.ide.util.treeView.AbstractTreeUi;
import com.intellij.ide.util.treeView.NodeDescriptorProvidingKey;
import com.intellij.ide.util.treeView.TreeAnchorizer;
import com.intellij.navigation.ItemPresentation;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

/**
 * File structure popup work with unique values (see {@link StructureViewTreeElement#getValue()}).
 * If it is needed to show the same PSI element several times then each occurrence of such psi should be wrapped to some unique object.
 * This class perform this task.
 * <p/>
 * This class could be moved to some common platform package if it will be needed for somebody else.
 * The implementation of this class is based on {@link PsiTreeElementBase} implementation (copy-pasted and modified).
 */
abstract class DuplicatedPsiTreeElementBase<T extends PsiElement & Navigatable> implements StructureViewTreeElement,
                                                                                           ItemPresentation,
                                                                                           NodeDescriptorProvidingKey {
  @NotNull
  private final StoredData<T> myValue;

  /** @param details is used to distinguish different occurrences of the same PSI element */
  protected DuplicatedPsiTreeElementBase(@NotNull T psiElement, @NotNull String details) {
    myValue = new StoredData<>(psiElement, details);
  }

  /** @return stored psi element */
  @NotNull
  public final T getElement() {
    return getValue().getElement();
  }

  /** @return additional information which is used to distinguish different occurrences of the same PSI element */
  @NotNull
  public final String getDetails() {
    return getValue().getDetails();
  }

  @NotNull
  public abstract Collection<StructureViewTreeElement> getChildrenBase();

  @NotNull
  @Override
  public ItemPresentation getPresentation() {
    return this;
  }

  @NotNull
  @Override
  public Object getKey() {
    return String.valueOf(getElement());
  }

  @Contract(pure = true)
  @NotNull
  @Override
  public final StoredData<T> getValue() {
    return myValue;
  }

  public String toString() {
    return getElement().toString();
  }

  @NotNull
  @Override
  public final StructureViewTreeElement[] getChildren() {
    return AbstractTreeUi.calculateYieldingToWriteAction(this::doGetChildren);
  }

  @NotNull
  private StructureViewTreeElement[] doGetChildren() {
    return PsiTreeElementBase.mergeWithExtensions(getElement(), getChildrenBase());
  }

  @Override
  public void navigate(boolean requestFocus) {
    getElement().navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getElement().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Contract(value = "null -> false", pure = true)
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final DuplicatedPsiTreeElementBase that = (DuplicatedPsiTreeElementBase)o;

    return getValue().equals(that.getValue());
  }

  public int hashCode() {
    return getValue().hashCode();
  }

  public boolean isValid() {
    return true;
  }

  /** This class is a wrapper over PSI element with additional information */
  private static class StoredData<T extends Navigatable> implements Navigatable {
    @NotNull
    private final Object myAnchor;
    @NotNull
    private final String myDetails;

    public StoredData(@NotNull T element, @NotNull String details) {
      myAnchor = TreeAnchorizer.getService().createAnchor(element);
      myDetails = details;
    }

    @NotNull
    public T getElement() {
      @SuppressWarnings("unchecked")
      T element = (T)TreeAnchorizer.getService().retrieveElement(myAnchor);
      assert element != null;
      return element;
    }

    @NotNull
    public String getDetails() {
      return myDetails;
    }

    @Override
    public void navigate(boolean requestFocus) {
      getElement().navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return getElement().canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return getElement().canNavigateToSource();
    }

    @Contract(value = "null -> false", pure = true)
    @Override
    public boolean equals(@Nullable Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      StoredData<?> data = (StoredData<?>)o;
      return Objects.equals(getElement(), data.getElement()) &&
             Objects.equals(getDetails(), data.getDetails());
    }

    @Override
    public int hashCode() {
      return Objects.hash(getElement(), getDetails());
    }
  }
}
