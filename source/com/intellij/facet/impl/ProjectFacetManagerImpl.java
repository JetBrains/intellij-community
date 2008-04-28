package com.intellij.facet.impl;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetManagerImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.module.impl.RemoveInvalidElementsDialog;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
@State(
    name = ProjectFacetManagerImpl.COMPONENT_NAME,
    storages = {
        @Storage(
            id="other",
            file="$PROJECT_FILE$"
        )
    }
)
public class ProjectFacetManagerImpl extends ProjectFacetManagerEx implements ProjectComponent, PersistentStateComponent<ProjectFacetManagerImpl.ProjectFacetManagerState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFacetManager";
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetManagerImpl");
  private ProjectFacetManagerState myState = new ProjectFacetManagerState();
  private List<FacetLoadingErrorDescription> myFacetLoadingErrors = new ArrayList<FacetLoadingErrorDescription>();
  private final Project myProject;

  public ProjectFacetManagerImpl(Project project) {
    myProject = project;
  }

  public void projectOpened() {
    if (!myFacetLoadingErrors.isEmpty()) {
      Application application = ApplicationManager.getApplication();
      if (application.isHeadlessEnvironment()) {
        throw new RuntimeException(myFacetLoadingErrors.get(0).getDescription());
      }

      Runnable runnable = new Runnable() {
        public void run() {
          Collection<FacetLoadingErrorDescription> toRemove =
              RemoveInvalidElementsDialog.showDialog(myProject, ProjectBundle.message("dialog.title.cannot.load.facets"),
                                                     ProjectBundle.message("error.message.cannot.load.facets"),
                                                     ProjectBundle.message("confirmation.message.would.you.like.to.remove.facet"),
                                                     myFacetLoadingErrors);
          myFacetLoadingErrors.clear();
          for (FacetLoadingErrorDescription errorDescription : toRemove) {
            FacetManagerImpl manager = (FacetManagerImpl)FacetManagerImpl.getInstance(errorDescription.getModule());
            manager.removeInvalidFacet(errorDescription.getUnderlyingFacet(), errorDescription.getState());
          }
        }
      };
      application.invokeLater(runnable, ModalityState.NON_MODAL);
    }
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public ProjectFacetManagerState getState() {
    return myState;
  }

  public void loadState(final ProjectFacetManagerState state) {
    myState = state;
  }

  public <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull final FacetType<?, C> facetType) {
    C configuration = facetType.createDefaultConfiguration();
    DefaultFacetConfigurationState state = myState.getDefaultConfigurations().get(facetType.getStringId());
    if (state != null) {
      Element defaultConfiguration = state.getDefaultConfiguration();
      if (defaultConfiguration != null) {
        try {
          configuration.readExternal(defaultConfiguration);
        }
        catch (InvalidDataException e) {
          LOG.info(e);
        }
      }
    }
    return configuration;
  }

  public <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull final FacetType<?, C> facetType, @NotNull final C configuration) {
    Map<String, DefaultFacetConfigurationState> defaultConfigurations = myState.getDefaultConfigurations();
    DefaultFacetConfigurationState state = defaultConfigurations.get(facetType.getStringId());
    if (state == null) {
      state = new DefaultFacetConfigurationState();
      defaultConfigurations.put(facetType.getStringId(), state);
    }
    Element element = new Element("_239");
    try {
      configuration.writeExternal(element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
    state.setDefaultConfiguration(element);
  }

  public void registerFacetLoadingError(@NotNull final FacetLoadingErrorDescription errorDescription) {
    myFacetLoadingErrors.add(errorDescription);
  }

  @Tag("default-facet-configuration")
  public static class DefaultFacetConfigurationState {
    private Element myDefaultConfiguration;

    @Tag("configuration")
    public Element getDefaultConfiguration() {
      return myDefaultConfiguration;
    }

    public void setDefaultConfiguration(final Element defaultConfiguration) {
      myDefaultConfiguration = defaultConfiguration;
    }
  }

  public static class ProjectFacetManagerState {
    private Map<String, DefaultFacetConfigurationState> myDefaultConfigurations = new HashMap<String, DefaultFacetConfigurationState>();

    @Tag("default-configurations")
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, //entryTagName = "default-configuration",
                   keyAttributeName = "facet-type")
    public Map<String, DefaultFacetConfigurationState> getDefaultConfigurations() {
      return myDefaultConfigurations;
    }

    public void setDefaultConfigurations(final Map<String, DefaultFacetConfigurationState> defaultConfigurations) {
      myDefaultConfigurations = defaultConfigurations;
    }
  }
}
