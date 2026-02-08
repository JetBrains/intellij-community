// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerListener;
import com.intellij.facet.FacetType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;


public abstract class LibraryContributingFacet<T extends FacetConfiguration> extends Facet<T> {
  public static final @NonNls String PYTHON_FACET_LIBRARY_NAME_SUFFIX = " interpreter library";

  public LibraryContributingFacet(@NotNull FacetType facetType,
                                  @NotNull Module module,
                                  @NotNull String name, @NotNull T configuration, Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
    final MessageBusConnection connection = module.getProject().getMessageBus().connect();
    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerListener() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ApplicationManager.getApplication().invokeLater(() -> ((LibraryContributingFacet<?>)facet).removeLibrary(), module.getDisposed());
        }
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ApplicationManager.getApplication().invokeLater(() -> ((LibraryContributingFacet<?>) facet).updateLibrary(), module.getDisposed());
        }
      }
    });
    Disposer.register(this, connection);
  }

  public abstract void updateLibrary();
  public abstract void removeLibrary();
}
