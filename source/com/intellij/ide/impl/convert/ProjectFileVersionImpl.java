/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.ide.impl.convert;

import com.intellij.CommonBundle;
import com.intellij.facet.FacetTypeId;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.picocontainer.MutablePicoContainer;

import java.io.IOException;

/**
 * @author nik
 */
@State(
  name = ProjectFileVersionImpl.COMPONENT_NAME,
  storages = {
    @Storage(
      id="other",
      file = "$PROJECT_FILE$"
    )
  }
)
public class ProjectFileVersionImpl extends ProjectFileVersion implements ProjectComponent, PersistentStateComponent<ProjectFileVersionImpl.ProjectFileVersionState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFileVersion";
  @NonNls public static final String CONVERTED_ATTRIBUTE = "converted";
  private ProjectFileVersionState myState = new ProjectFileVersionState();
  private Project myProject;

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  public ProjectFileVersionImpl(final Project project) {
    myProject = project;
    myState.myConverted = Boolean.toString(project.getPicoContainer().getComponentInstance(ProjectConversionHelper.class) == null);
  }

  @NonNls
  @NotNull
  public String getComponentName() {
    return COMPONENT_NAME;
  }

  public void initComponent() {
  }

  @NotNull
  private AllowedFeaturesFilter getFeaturesFilter() {
    if (isConverted()) {
      return AllowedFeaturesFilter.ALL_ALLOWED;
    }
    final ConverterFactory[] extensions = Extensions.getExtensions(ConverterFactory.EXTENSION_POINT);
    for (ConverterFactory converterFactory : extensions) {
      final AllowedFeaturesFilter filter = converterFactory.getUnconvertedProjectFeaturesFilter();
      if (filter != null) {
        return filter;
      }
    }
    return AllowedFeaturesFilter.ALL_ALLOWED;
  }

  public void disposeComponent() {
  }

  public boolean isConverted() {
    return Boolean.parseBoolean(myState.myConverted);
  }

  public boolean convert() throws IOException {
    final MutablePicoContainer picoContainer = ((ProjectImpl)myProject).getPicoContainer();
    ProjectConversionHelper conversionHelper = (ProjectConversionHelper)picoContainer.getComponentInstance(ProjectConversionHelper.class);
    picoContainer.unregisterComponent(ProjectConversionHelper.class);
    try {
      final boolean saved = ((ProjectEx)myProject).getStateStore().save();
      if (!saved) {
        picoContainer.registerComponentInstance(ProjectConversionHelper.class, conversionHelper);
        return false;
      }
      myState.myConverted = Boolean.toString(true);
      return true;
    }
    catch (IOException e) {
      picoContainer.registerComponentInstance(ProjectConversionHelper.class, conversionHelper);
      throw e;
    }
  }

  public boolean isFacetAdditionEnabled(final FacetTypeId<?> facetType) {
    if (getFeaturesFilter().isFacetAdditionEnabled(facetType)) {
      return true;
    }
    showNotAllowedMessage();
    return false;
  }

  public boolean isFacetDeletionEnabled(final FacetTypeId<?> facetType) {
    if (getFeaturesFilter().isFacetDeletionEnabled(facetType)) {
      return true;
    }
    showNotAllowedMessage();
    return false;
  }

  public ProjectFileVersionState getState() {
    return myState;
  }

  public void loadState(final ProjectFileVersionState object) {
    myState = object;
  }

  public void showNotAllowedMessage() {
    Messages.showErrorDialog(myProject, IdeBundle.message("message.text.this.feature.is.not.available.for.project.in.older.format"), CommonBundle.getErrorTitle());
  }

  public static class ProjectFileVersionState {
    @Attribute(CONVERTED_ATTRIBUTE)
    public String myConverted;

  }
}
