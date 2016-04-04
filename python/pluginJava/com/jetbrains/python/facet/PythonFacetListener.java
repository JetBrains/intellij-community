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

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetManagerAdapter;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleComponent;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonFacetListener implements ModuleComponent {
  private MessageBusConnection myConnection;
  private final Module myModule;

  public PythonFacetListener(Module module) {
    myModule = module;
  }

  public void initComponent() {
    myConnection = myModule.getMessageBus().connect();
    myConnection.subscribe(FacetManager.FACETS_TOPIC, new FacetManagerAdapter() {
      @Override
      public void beforeFacetRemoved(@NotNull Facet facet) {
        if (facet instanceof LibraryContributingFacet) {
          ((LibraryContributingFacet) facet).removeLibrary();
        }
      }

      @Override
      public void facetConfigurationChanged(@NotNull Facet facet) {
        if (facet instanceof LibraryContributingFacet) {
          ((LibraryContributingFacet) facet).updateLibrary();
        }
      }
    });
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public void moduleAdded() {
  }

  @NotNull
  public String getComponentName() {
    return "PythonFacetListener";
  }

  public void disposeComponent() {
    myConnection.disconnect();
  }
}
