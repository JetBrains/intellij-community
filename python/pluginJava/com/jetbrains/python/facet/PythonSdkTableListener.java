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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.util.messages.MessageBus;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PythonSdkTableListener implements ApplicationComponent {
  public PythonSdkTableListener(MessageBus messageBus) {
    ProjectJdkTable.Listener jdkTableListener = new ProjectJdkTable.Listener() {
      public void jdkAdded(final Sdk sdk) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              ApplicationManager.getApplication().runWriteAction(new Runnable() {
                public void run() {
                  addLibrary(sdk);
                }
              });
            }
          });
        }
      }

      public void jdkRemoved(final Sdk sdk) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          removeLibrary(sdk);
        }
      }

      public void jdkNameChanged(final Sdk sdk, final String previousName) {
        if (sdk.getSdkType() instanceof PythonSdkType) {
          renameLibrary(sdk, previousName);
        }
      }
    };
    messageBus.connect().subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, jdkTableListener);
  }

  static Library addLibrary(Sdk sdk) {
    final LibraryTable.ModifiableModel libraryTableModel = ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
    final Library library = libraryTableModel.createLibrary(PythonFacet.getFacetLibraryName(sdk.getName()));
    final Library.ModifiableModel model = library.getModifiableModel();
    for (String url : sdk.getRootProvider().getUrls(OrderRootType.CLASSES)) {
      model.addRoot(url, OrderRootType.CLASSES);
      model.addRoot(url, OrderRootType.SOURCES);
    }
    model.commit();
    libraryTableModel.commit();
    return library;
  }

  private static void removeLibrary(final Sdk sdk) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LibraryTable.ModifiableModel libraryTableModel =
              ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(PythonFacet.getFacetLibraryName(sdk.getName()));
            if (library != null) {
              libraryTableModel.removeLibrary(library);
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  private static void renameLibrary(final Sdk sdk, final String previousName) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            final LibraryTable.ModifiableModel libraryTableModel =
              ModifiableModelsProvider.SERVICE.getInstance().getLibraryTableModifiableModel();
            final Library library = libraryTableModel.getLibraryByName(PythonFacet.getFacetLibraryName(previousName));
            if (library != null) {
              final Library.ModifiableModel model = library.getModifiableModel();
              model.setName(PythonFacet.getFacetLibraryName(sdk.getName()));
              model.commit();
            }
            libraryTableModel.commit();
          }
        });
      }
    }, ModalityState.NON_MODAL);
  }

  @NotNull
  public String getComponentName() {
    return "PythonSdkTableListener";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }
}
