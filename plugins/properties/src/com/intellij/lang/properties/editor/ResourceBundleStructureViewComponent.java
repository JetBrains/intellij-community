/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.properties.editor;

import com.intellij.find.findUsages.PsiElement2UsageTargetAdapter;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.actions.ContextHelpAction;
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
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ResourceBundleStructureViewComponent extends PropertiesGroupingStructureViewComponent {
  private final static Logger LOG = Logger.getInstance(ResourceBundleStructureViewComponent.class);

  private final ResourceBundle myResourceBundle;

  public ResourceBundleStructureViewComponent(final ResourceBundle resourceBundle,
                                              final ResourceBundleEditor editor,
                                              final PropertiesAnchorizer anchorizer) {
    super(resourceBundle.getProject(), editor, new ResourceBundleStructureViewModel(resourceBundle, anchorizer));
    myResourceBundle = resourceBundle;
    tunePopupActionGroup();
    getTree().setCellRenderer(new ResourceBundleEditorRenderer());
  }

  @Override
  protected ActionGroup createActionGroup() {
    final DefaultActionGroup result = (DefaultActionGroup) super.createActionGroup();
    result.add(new ContextHelpAction(getHelpID()), Constraints.LAST);
    return result;
  }

  @Override
  protected void addGroupByActions(final DefaultActionGroup result) {
    super.addGroupByActions(result);
    result.add(new NewPropertyAction(true), Constraints.FIRST);
  }

  private void tunePopupActionGroup() {
    final DefaultActionGroup propertiesPopupGroup = new DefaultActionGroup();
    propertiesPopupGroup.copyFromGroup((DefaultActionGroup)ActionManager.getInstance().getAction(IdeActions.GROUP_STRUCTURE_VIEW_POPUP));
    propertiesPopupGroup.add(Separator.getInstance(), Constraints.FIRST);
    propertiesPopupGroup.add(new NewPropertyAction(true), Constraints.FIRST);
    PopupHandler.installPopupHandler(getTree(), propertiesPopupGroup, IdeActions.GROUP_STRUCTURE_VIEW_POPUP, ActionManager.getInstance());
  }

  @NotNull
  private PsiFile[] getSelectedPsiFiles() {
    for (ResourceBundleEditorViewElement element : ((ResourceBundleEditor)getFileEditor()).getSelectedElements()) {
      if (element.getFiles() != null) {
        return element.getFiles();
      }
    }
    return PsiFile.EMPTY_ARRAY;
  }

  public Object getData(final String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    } else if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return getFileEditor();
    }
    else if (ResourceBundle.ARRAY_DATA_KEY.is(dataId)) {
      return new ResourceBundle[]{myResourceBundle};
    }
    else if (IProperty.ARRAY_KEY.is(dataId)) {
      final Collection<ResourceBundleEditorViewElement> selectedElements = ((ResourceBundleEditor)getFileEditor()).getSelectedElements();
      if (selectedElements.isEmpty()) {
        return null;
      }
      else if (selectedElements.size() == 1) {
        return ContainerUtil.getFirstItem(selectedElements).getProperties();
      }
      else {
        return ContainerUtil.toArray(ContainerUtil.flatten(
          ContainerUtil.mapNotNull(selectedElements, (NullableFunction<ResourceBundleEditorViewElement, List<IProperty>>)element -> {
            final IProperty[] properties = element.getProperties();
            return properties == null ? null : ContainerUtil.newArrayList(properties);
          })), IProperty[]::new);
      }
    }
    else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final List<PsiElement> elements = new ArrayList<>();
      Collections.addAll(elements, getSelectedPsiFiles());
      final IProperty[] properties = (IProperty[])getData(IProperty.ARRAY_KEY.getName());
      if (properties != null) {
        for (IProperty property : properties) {
          final PsiElement element = property.getPsiElement();
          if (element.isValid()) {
            elements.add(element);
          }
        }
      }
      return elements.toArray(new PsiElement[elements.size()]);
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      if (getSelectedPsiFiles().length != 0) {
        return new ResourceBundleDeleteProvider();
      }
      final IProperty[] properties = IProperty.ARRAY_KEY.getData(this);
      if (properties != null && properties.length != 0) {
        return new PropertiesDeleteProvider(((ResourceBundleEditor)getFileEditor()).getPropertiesInsertDeleteManager(), properties);
      }
    }
    else if (UsageView.USAGE_TARGETS_KEY.is(dataId)) {
      final PsiElement[] chosenElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
      if (chosenElements != null) {
        final UsageTarget[] usageTargets = new UsageTarget[chosenElements.length];
        for (int i = 0; i < chosenElements.length; i++) {
          usageTargets[i] = new PsiElement2UsageTargetAdapter(chosenElements[i]);
        }
        return usageTargets;
      }
    }
    else if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return new CopyProvider() {
        @Override
        public void performCopy(@NotNull final DataContext dataContext) {
          final PsiElement[] selectedPsiElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
          if (selectedPsiElements != null) {
            final List<String> names = new ArrayList<>(selectedPsiElements.length);
            for (final PsiElement element : selectedPsiElements) {
              if (element instanceof PsiNamedElement) {
                names.add(((PsiNamedElement)element).getName());
              }
            }
            CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(names, "\n")));
          }
        }

        @Override
        public boolean isCopyEnabled(@NotNull final DataContext dataContext) {
          return true;
        }

        @Override
        public boolean isCopyVisible(@NotNull final DataContext dataContext) {
          return true;
        }
      };
    }
    return super.getData(dataId);
  }

  protected boolean showScrollToFromSourceActions() {
    return false;
  }

  private class PropertiesDeleteProvider implements DeleteProvider {
    private final ResourceBundlePropertiesUpdateManager myInsertDeleteManager;
    private final IProperty[] myProperties;

    private PropertiesDeleteProvider(ResourceBundlePropertiesUpdateManager insertDeleteManager, final IProperty[] properties) {
      myInsertDeleteManager = insertDeleteManager;
      myProperties = properties;
    }

    @Override
    public void deleteElement(@NotNull final DataContext dataContext) {
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
    public boolean canDeleteElement(@NotNull final DataContext dataContext) {
      return true;
    }
  }

  @NonNls
  public String getHelpID() {
    return "editing.propertyFile.bundleEditor";
  }
}

