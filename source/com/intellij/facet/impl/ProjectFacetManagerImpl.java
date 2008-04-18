package com.intellij.facet.impl;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.ProjectFacetManager;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
@State(
    name = "ProjectFacetManager",
    storages = {
        @Storage(
            id="other",
            file="$PROJECT_FILE$"
        )
    }
)
public class ProjectFacetManagerImpl extends ProjectFacetManager implements PersistentStateComponent<ProjectFacetManagerImpl.ProjectFacetManagerState> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetManagerImpl");
  private ProjectFacetManagerState myState = new ProjectFacetManagerState();

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
