package com.intellij.openapi.roots.ui.configuration.projectRoot;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.facet.impl.ui.facetType.FacetTypeEditor;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManager;
import com.intellij.facet.impl.autodetecting.FacetAutodetectingManagerImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.Result;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author nik
 */
@State(
    name = "FacetStructureConfigurable.UI",
    storages = {
      @Storage(
          id = "other",
          file = "$WORKSPACE_FILE$"
      )
    }
)
public class FacetStructureConfigurable extends BaseStructureConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/modules/modules.png");//todo[nik] use facets icon
  private final ModuleManager myModuleManager;
  private final Map<FacetType<?, ?>, FacetTypeEditor> myFacetTypeEditors = new HashMap<FacetType<?,?>, FacetTypeEditor>();

  public FacetStructureConfigurable(final Project project, ModuleManager moduleManager) {
    super(project);
    myModuleManager = moduleManager;
  }

  protected void loadTree() {
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    for (FacetType<?,?> facetType : FacetTypeRegistry.getInstance().getFacetTypes()) {
      FacetTypeConfigurable facetTypeConfigurable = new FacetTypeConfigurable(this, facetType);
      MyNode facetTypeNode = new MyNode(facetTypeConfigurable);
      addNode(facetTypeNode, myRoot);

      for (Module module : myModuleManager.getModules()) {
        Collection<? extends Facet> facets = FacetManager.getInstance(module).getFacetsByType(facetType.getId());
        for (Facet facet : facets) {
          FacetEditorFacadeImpl editorFacade = ModuleStructureConfigurable.getInstance(myProject).getFacetEditorFacade();
          FacetConfigurable facetConfigurable = editorFacade.getOrCreateConfigurable(facet);
          addNode(new FacetConfigurableNode(facetConfigurable), facetTypeNode);
        }
      }
    }
  }

  public FacetTypeEditor getOrCreateFacetTypeEditor(@NotNull FacetType<?, ?> facetType) {
    FacetTypeEditor editor = myFacetTypeEditors.get(facetType);
    if (editor == null) {
      editor = new FacetTypeEditor(myProject, myContext, facetType);
      editor.reset();
      myFacetTypeEditors.put(facetType, editor);
    }
    return editor;
  }

  public void reset() {
    super.reset();
    myFacetTypeEditors.clear();
  }


  public void apply() throws ConfigurationException {
    super.apply();
    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.apply();
    }
    new WriteAction() {
      protected void run(final Result result) {
        ((FacetAutodetectingManagerImpl)FacetAutodetectingManager.getInstance(myProject)).redetectFacets();
      }
    }.execute();
  }

  public boolean isModified() {
    return super.isModified() || isEditorsModified();
  }

  private boolean isEditorsModified() {
    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      if (editor.isModified()) {
        return true;
      }
    }
    return false;
  }

  public void disposeUIResources() {
    super.disposeUIResources();

    for (FacetTypeEditor editor : myFacetTypeEditors.values()) {
      editor.disposeUIResources();
    }
    myFacetTypeEditors.clear();
  }

  @NotNull
  protected ArrayList<AnAction> createActions(final boolean fromPopup) {
    ArrayList<AnAction> actions = new ArrayList<AnAction>();
    addCollapseExpandActions(actions);
    return actions;
  }

  protected AbstractAddGroup createAddAction() {
    return null;
  }

  protected void processRemovedItems() {
  }

  protected boolean wasObjectStored(final Object editableObject) {
    return false;
  }

  public String getDisplayName() {
    return ProjectBundle.message("project.facets.display.name");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return "concepts.facet";//todo[nik]
  }

  public String getId() {
    return "project.facets";
  }

  public boolean clearSearch() {
    return false;
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  public void dispose() {
  }

  private class FacetConfigurableNode extends MyNode {
    public FacetConfigurableNode(final FacetConfigurable facetConfigurable) {
      super(facetConfigurable);
    }

    @NotNull
    public String getDisplayName() {
      FacetConfigurable facetConfigurable = (FacetConfigurable)getConfigurable();
      String moduleName = myContext.getRealName(facetConfigurable.getEditableObject().getModule());
      return facetConfigurable.getDisplayName() + " (" + moduleName + ")";
    }
  }
}
