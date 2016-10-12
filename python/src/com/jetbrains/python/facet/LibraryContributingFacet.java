/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.facet;

import com.intellij.facet.*;
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
    final MessageBusConnection connection = module.getMessageBus().connect();
    connection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ((LibraryContributingFacet) facet).removeLibrary();
        }
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet == LibraryContributingFacet.this) {
          ((LibraryContributingFacet) facet).updateLibrary();
        }
      }
    });
    Disposer.register(this, connection);
  }

  public abstract void updateLibrary();
  public abstract void removeLibrary();
}
