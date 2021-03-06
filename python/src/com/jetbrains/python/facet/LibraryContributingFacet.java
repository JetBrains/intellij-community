// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.facet;

import com.intellij.facet.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class LibraryContributingFacet<T extends FacetConfiguration> extends Facet<T> {
  @NonNls public static final String PYTHON_FACET_LIBRARY_NAME_SUFFIX = " interpreter library";

  public LibraryContributingFacet(@NotNull FacetType facetType,
                                  @NotNull Module module,
                                  @NotNull String name, @NotNull T configuration, Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
    final MessageBusConnection connection = module.getProject().getMessageBus().connect();
    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerListener() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ApplicationManager.getApplication().invokeLater(() -> ((LibraryContributingFacet<?>)facet).removeLibrary());
        }
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ApplicationManager.getApplication().invokeLater(() -> ((LibraryContributingFacet<?>) facet).updateLibrary());
        }
      }
    });
    Disposer.register(this, connection);
  }

  public abstract void updateLibrary();
  public abstract void removeLibrary();
}
