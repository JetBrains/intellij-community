// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.editor;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.ContextHelpAction;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.editor.inspections.ResourceBundleEditorRenderer;
import com.intellij.lang.properties.projectView.ResourceBundleDeleteProvider;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;

public class ResourceBundleStructureViewComponent extends PropertiesGroupingStructureViewComponent {
  private static final Logger LOG = Logger.getInstance(ResourceBundleStructureViewComponent.class);

  private final ResourceBundle myResourceBundle;

  public ResourceBundleStructureViewComponent(@NotNull ResourceBundle resourceBundle,
                                              @NotNull ResourceBundleEditor editor) {
    super(resourceBundle.getProject(), editor, new ResourceBundleStructureViewModel(resourceBundle));
    myResourceBundle = resourceBundle;
    tunePopupActionGroup();
    getTree().setCellRenderer(new ResourceBundleEditorRenderer());
    showToolbar();
  }

  @Override
  protected @NotNull ActionGroup createActionGroup() {
    final DefaultActionGroup result = (DefaultActionGroup) super.createActionGroup();
    result.add(new ContextHelpAction(getHelpID()), Constraints.LAST);
    return result;
  }

  @Override
  protected void addGroupByActions(final @NotNull DefaultActionGroup result) {
    super.addGroupByActions(result);
    result.add(createSettingsActionGroup());
    result.add(new NewPropertyAction(true), Constraints.FIRST);
  }

  private ActionGroup createSettingsActionGroup() {
    DefaultActionGroup actionGroup = DefaultActionGroup.createPopupGroup(ResourceBundleEditorBundle.messagePointer("resource.bundle.editor.settings.action.title"));
    final Presentation presentation = actionGroup.getTemplatePresentation();
    presentation.setIcon(AllIcons.General.GearPlain);
    actionGroup.add(new ResourceBundleEditorKeepEmptyValueToggleAction());

    actionGroup.add(new ToggleAction(ResourceBundleEditorBundle.message("show.only.incomplete.action.text"), null, AllIcons.General.Error) {
      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public boolean isSelected(@NotNull AnActionEvent e) {
        return ((ResourceBundleStructureViewModel)getTreeModel()).isShowOnlyIncomplete();
      }

      @Override
      public void setSelected(@NotNull AnActionEvent e, boolean state) {
        ((ResourceBundleStructureViewModel)getTreeModel()).setShowOnlyIncomplete(state);
        rebuild();
      }
    });

    return actionGroup;
  }

  private void tunePopupActionGroup() {
    final DefaultActionGroup propertiesPopupGroup = new DefaultActionGroup();
    propertiesPopupGroup.copyFromGroup((DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_STRUCTURE_VIEW_POPUP));
    propertiesPopupGroup.add(Separator.getInstance(), Constraints.FIRST);
    propertiesPopupGroup.add(new NewPropertyAction(true), Constraints.FIRST);
    PopupHandler.installPopupMenu(getTree(), propertiesPopupGroup, IdeActions.GROUP_STRUCTURE_VIEW_POPUP);
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    super.uiDataSnapshot(sink);
    sink.set(ResourceBundle.ARRAY_DATA_KEY, new ResourceBundle[]{myResourceBundle});

    JBIterable<Object> selection = JBIterable.of(getTree().getSelectionPaths()).map(TreeUtil::getLastUserObject);
    sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, new MyDeleteProvider());
    sink.set(PlatformDataKeys.COPY_PROVIDER, new MyCopyProvider());
    sink.lazy(CommonDataKeys.VIRTUAL_FILE, () -> {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    });
    sink.lazy(IProperty.ARRAY_KEY, () -> {
      List<IProperty> list = selection
        .filterMap(StructureViewComponent::unwrapWrapper)
        .filter(ResourceBundleEditorViewElement.class)
        .flatten(o -> JBIterable.of(o.getProperties()))
        .toList();
      return list.isEmpty() ? null : list.toArray(IProperty.EMPTY_ARRAY);
    });
    sink.lazy(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, () -> {
      List<PsiElement> list = getPsiElements(selection);
      return list.toArray(PsiElement.EMPTY_ARRAY);
    });
    sink.lazy(UsageView.USAGE_TARGETS_KEY, () -> {
      List<PsiElement> chosenElements = getPsiElements(selection);
      if (chosenElements.isEmpty()) return null;
      return ContainerUtil.map2Array(
        chosenElements, UsageTarget.EMPTY_ARRAY,
        o -> new PsiElement2UsageTargetAdapter(o, true));
    });
  }

  private static @Unmodifiable @NotNull List<PsiElement> getPsiElements(JBIterable<Object> selection) {
    return selection
      .filterMap(StructureViewComponent::unwrapWrapper)
      .filter(ResourceBundleEditorViewElement.class)
      .flatten(o -> JBIterable.<PsiElement>of(o.getFiles())
        .append(JBIterable.of(o.getProperties())
                  .map(IProperty::getPsiElement)
                  .filter(PsiElement::isValid)))
      .toList();
  }

  @Override
  protected boolean showScrollToFromSourceActions() {
    return false;
  }

  private static class MyCopyProvider implements CopyProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      PsiElement[] selectedPsiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (selectedPsiElements != null) {
        List<String> names = new ArrayList<>(selectedPsiElements.length);
        for (PsiElement element : selectedPsiElements) {
          if (element instanceof PsiNamedElement) {
            names.add(((PsiNamedElement)element).getName());
          }
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(names, "\n")));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return true;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }
  }

  private class MyDeleteProvider implements DeleteProvider {
    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      PsiElement[] selectedPsiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (selectedPsiElements == null) return;
      List<PsiFile> psiFiles = ContainerUtil.findAll(selectedPsiElements, PsiFile.class);
      DeleteProvider delegate;
      if (!psiFiles.isEmpty()) {
        delegate = new ResourceBundleDeleteProvider();
      }
      else {
        IProperty[] properties = IProperty.ARRAY_KEY.getData(dataContext);
        if (properties != null && properties.length != 0) {
          delegate = new PropertiesDeleteProvider(((ResourceBundleEditor)getFileEditor()).getPropertiesInsertDeleteManager(), properties);
        }
        else {
          return;
        }
      }
      delegate.deleteElement(dataContext);
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return CommonDataKeys.PROJECT.getData(dataContext) != null;
    }
  }

  private final class PropertiesDeleteProvider implements DeleteProvider {
    private final ResourceBundlePropertiesUpdateManager myInsertDeleteManager;
    private final IProperty[] myProperties;

    private PropertiesDeleteProvider(ResourceBundlePropertiesUpdateManager insertDeleteManager, final IProperty[] properties) {
      myInsertDeleteManager = insertDeleteManager;
      myProperties = properties;
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    @Override
    public void deleteElement(final @NotNull DataContext dataContext) {
      final List<PropertiesFile> bundlePropertiesFiles = myResourceBundle.getPropertiesFiles();

      final List<PsiElement> toDelete = new ArrayList<>();
      for (IProperty property : myProperties) {
        final String key = property.getKey();
        if (key == null) {
          LOG.error("key must be not null " + property);
        } else {
          for (PropertiesFile propertiesFile : bundlePropertiesFiles) {
            for (final IProperty iProperty : propertiesFile.findPropertiesByKey(key)) {
              toDelete.add(iProperty.getPsiElement());
            }
          }
        }
      }
      final Project project = CommonDataKeys.PROJECT.getData(dataContext);
      LOG.assertTrue(project != null);
      new SafeDeleteHandler().invoke(project, PsiUtilCore.toPsiElementArray(toDelete), dataContext);
      myInsertDeleteManager.reload();
    }

    @Override
    public boolean canDeleteElement(final @NotNull DataContext dataContext) {
      return true;
    }
  }

  @Override
  public @NonNls String getHelpID() {
    return "editing.propertyFile.bundleEditor";
  }
}