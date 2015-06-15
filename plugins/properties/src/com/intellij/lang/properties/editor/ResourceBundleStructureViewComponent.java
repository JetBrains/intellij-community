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
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.safeDelete.SafeDeleteHandler;
import com.intellij.ui.PopupHandler;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.util.containers.ContainerUtil;
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

  public Object getData(final String dataId) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) {
      return new ResourceBundleAsVirtualFile(myResourceBundle);
    } else if (PlatformDataKeys.FILE_EDITOR.is(dataId)) {
      return getFileEditor();
    } else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      final Collection<ResourceBundleEditorViewElement> selectedElements = ((ResourceBundleEditor)getFileEditor()).getSelectedElements();
      if (selectedElements.isEmpty()) {
        return null;
      } else if (selectedElements.size() == 1) {
        return ContainerUtil.getFirstItem(selectedElements).getPsiElements();
      } else {
        final List<PsiElement> psiElements = new ArrayList<PsiElement>();
        for (ResourceBundleEditorViewElement selectedElement : selectedElements) {
          Collections.addAll(psiElements, selectedElement.getPsiElements());
        }
        return psiElements.toArray(new PsiElement[psiElements.size()]);
      }
    } else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId)) {
      final PsiElement[] psiElements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(this);
      if (psiElements != null && psiElements.length > 0) {
        return new PsiElementsDeleteProvider(((ResourceBundleEditor)getFileEditor()).getPropertiesInsertDeleteManager(), psiElements);
      }
    } else if (UsageView.USAGE_TARGETS_KEY.is(dataId)) {
      final PsiElement[] chosenElements = (PsiElement[]) getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
      if (chosenElements != null) {
        final UsageTarget[] usageTargets = new UsageTarget[chosenElements.length];
        for (int i = 0; i < chosenElements.length; i++) {
          usageTargets[i] = new PsiElement2UsageTargetAdapter(chosenElements[i]);
        }
        return usageTargets;
      }
    } else if (PlatformDataKeys.COPY_PROVIDER.is(dataId)) {
      return new CopyProvider() {
        @Override
        public void performCopy(@NotNull final DataContext dataContext) {
          final PsiElement[] selectedPsiElements = (PsiElement[])getData(LangDataKeys.PSI_ELEMENT_ARRAY.getName());
          if (selectedPsiElements != null) {
            final List<String> names = new ArrayList<String>(selectedPsiElements.length);
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

  private class PsiElementsDeleteProvider implements DeleteProvider {
    private final ResourceBundlePropertiesUpdateManager myInsertDeleteManager;
    private final PsiElement[] myElements;

    private PsiElementsDeleteProvider(ResourceBundlePropertiesUpdateManager insertDeleteManager, final PsiElement[] elements) {
      myInsertDeleteManager = insertDeleteManager;
      myElements = elements;
    }

    @Override
    public void deleteElement(@NotNull final DataContext dataContext) {
      final List<PropertiesFile> bundlePropertiesFiles = myResourceBundle.getPropertiesFiles();

      final List<PsiElement> toDelete = new ArrayList<PsiElement>();
      for (PsiElement element : myElements) {
        final Property property = (Property) element;
        final String key = property.getKey();
        if (key == null) {
          LOG.error("key must be not null " + element);
        } else {
          for (PropertiesFile propertiesFile : bundlePropertiesFiles) {
            for (final IProperty iProperty : propertiesFile.findPropertiesByKey(key)) {
              toDelete.add(iProperty.getPsiElement());
            }
          }
        }
      }

      new SafeDeleteHandler().invoke(myElements[0].getProject(), PsiUtilCore.toPsiElementArray(toDelete), dataContext);
      myInsertDeleteManager.reload();
    }

    @Override
    public boolean canDeleteElement(@NotNull final DataContext dataContext) {
      return true;
    }
  }
}

