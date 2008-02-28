package com.intellij.openapi.components.impl.stores;

import com.intellij.ide.impl.convert.ProjectConversionHelper;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.project.impl.convertors.Convertor01;
import com.intellij.openapi.project.impl.convertors.Convertor12;
import com.intellij.openapi.project.impl.convertors.Convertor23;
import com.intellij.openapi.project.impl.convertors.Convertor34;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author mike
 */
public class IdeaProjectStoreImpl extends ProjectWithModulesStoreImpl {
  public IdeaProjectStoreImpl(final ProjectEx project) {
    super(project);
  }

  protected StateStorageManager createStateStorageManager() {
    return new ProjectStateStorageManager(PathMacroManager.getInstance(getComponentManager()).createTrackingSubstitutor(), myProject) {
      public XmlElementStorage.StorageData createWsStorageData() {
        return new IdeaWsStorageData(ROOT_TAG_NAME, myProject);
      }

      public XmlElementStorage.StorageData createIprStorageData() {
        return new IdeaIprStorageData(ROOT_TAG_NAME, myProject);
      }
    };
  }

  @Nullable
  private static ProjectConversionHelper getConversionHelper(Project project) {
    return (ProjectConversionHelper)project.getPicoContainer().getComponentInstance(ProjectConversionHelper.class);
  }

  private class IdeaWsStorageData extends WsStorageData {
    public IdeaWsStorageData(final String rootElementName, final Project project) {
      super(rootElementName, project);
    }

    public IdeaWsStorageData(final WsStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaWsStorageData(this);
    }

    protected void load(@NotNull final Element rootElement) throws IOException {
      final ProjectConversionHelper conversionHelper = getConversionHelper(myProject);

      if (conversionHelper != null) {
        conversionHelper.convertWorkspaceRootToNewFormat(rootElement);
      }

      super.load(rootElement);
    }

    @NotNull
    protected Element save() {
      final Element result = super.save();

      final ProjectConversionHelper conversionHelper = getConversionHelper(myProject);

      if (conversionHelper != null) {
        conversionHelper.convertWorkspaceRootToOldFormat(result);
      }

      return result;
    }
  }

  private class IdeaIprStorageData extends IprStorageData {

    public IdeaIprStorageData(final String rootElementName, Project project) {
      super(rootElementName, project);
    }

    public IdeaIprStorageData(final IprStorageData storageData) {
      super(storageData);
    }

    public XmlElementStorage.StorageData clone() {
      return new IdeaIprStorageData(this);
    }

    protected void convert(final Element root, final int originalVersion) {
      if (originalVersion < 1) {
        Convertor01.execute(root);
      }
      if (originalVersion < 2) {
        Convertor12.execute(root);
      }
      if (originalVersion < 3) {
        Convertor23.execute(root);
      }
      if (originalVersion < 4) {
        Convertor34.execute(root, myFilePath, getConversionProblemsStorage());
      }
    }

  }
}
