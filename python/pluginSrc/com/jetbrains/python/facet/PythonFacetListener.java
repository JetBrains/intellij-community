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
