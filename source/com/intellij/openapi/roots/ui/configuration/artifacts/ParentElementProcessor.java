package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public abstract class ParentElementProcessor {

  public abstract boolean process(@NotNull CompositePackagingElement<?> element, @NotNull List<Pair<Artifact,CompositePackagingElement<?>>> parents,
                                  @NotNull Artifact artifact);

}
