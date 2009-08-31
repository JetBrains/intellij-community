package com.intellij.util.xml.model;

import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.DomElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * User: Sergey.Vasiliev
 */
public interface MultipleDomModelFactory<Scope extends UserDataHolder, T extends DomElement, M extends DomModel<T>> {
  @NotNull
  List<M> getAllModels(@NotNull Scope scope);

  Set<XmlFile> getAllConfigFiles(@NotNull Scope scope);

  @Nullable
  M getCombinedModel(@Nullable Scope scope);
}
