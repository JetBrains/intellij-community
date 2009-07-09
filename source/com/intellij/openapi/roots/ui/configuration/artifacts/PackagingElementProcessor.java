package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class PackagingElementProcessor<E extends PackagingElement<?>> {
  public abstract boolean process(@NotNull List<CompositePackagingElement<?>> parents, @NotNull E e);
}
