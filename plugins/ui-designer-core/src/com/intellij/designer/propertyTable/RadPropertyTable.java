/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.designer.propertyTable;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.*;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

public class RadPropertyTable extends PropertyTable implements DataProvider, ComponentSelectionListener {
  private final Project myProject;

  private EditableArea myArea;
  private DesignerEditorPanel myDesigner;
  private QuickFixManager myQuickFixManager;
  private PropertyTablePanel myPropertyTablePanel;

  public RadPropertyTable(@NotNull Project project) {
    setShowVerticalLines(true);
    setIntercellSpacing(new Dimension(1, 1));

    myProject = project;

    getModel().addTableModelListener(new TableModelListener() {
      @Override
      public void tableChanged(TableModelEvent e) {
        if (myPropertyTablePanel != null) {
          myPropertyTablePanel.updateActions();
        }
      }
    });
  }

  private final ListSelectionListener myListener = new ListSelectionListener() {
    @Override
    public void valueChanged(ListSelectionEvent e) {
      if (myDesigner != null) {
        myDesigner.setSelectionProperty(getCurrentKey(), getSelectionProperty());
      }
    }
  };

  private void addSelectionListener() {
    getSelectionModel().addListSelectionListener(myListener);
  }

  private void removeSelectionListener() {
    getSelectionModel().removeListSelectionListener(myListener);
  }

  public void initQuickFixManager(JViewport viewPort) {
    myQuickFixManager = new QuickFixManager(this, viewPort);
  }

  public void setPropertyTablePanel(PropertyTablePanel propertyTablePanel) {
    myPropertyTablePanel = propertyTablePanel;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.FILE_EDITOR.is(dataId) && myDesigner != null) {
      return myDesigner.getEditor();
    }
    return null;
  }

  @Override
  protected List<ErrorInfo> getErrors(@NotNull PropertiesContainer container) {
    return container instanceof RadComponent ? RadComponent.getError((RadComponent)container) : Collections.<ErrorInfo>emptyList();
  }

  @NotNull
  protected TextAttributesKey getErrorAttributes(@NotNull HighlightSeverity severity) {
    return SeverityRegistrar.getSeverityRegistrar(myProject).getHighlightInfoTypeBySeverity(severity).getAttributesKey();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public void setArea(@Nullable DesignerEditorPanel designer, @Nullable EditableArea area) {
    myDesigner = designer;
    myQuickFixManager.setDesigner(designer);

    if (myArea != null) {
      myArea.removeSelectionListener(this);
    }

    myArea = area;

    if (myArea != null) {
      myArea.addSelectionListener(this);
    }

    update();
  }

  @Override
  public void selectionChanged(EditableArea area) {
    update();
  }

  public void updateInspections() {
    myQuickFixManager.update();
  }

  @Override
  public void update() {
    try {
      removeSelectionListener();

      if (myArea == null) {
        update(Collections.<PropertiesContainer>emptyList(), null);
      }
      else {
        update(myArea.getSelection(), myDesigner.getSelectionProperty(getCurrentKey()));
      }
    }
    finally {
      addSelectionListener();
    }
  }

  @Nullable
  private String getCurrentKey() {
    PropertyTableTab tab = myPropertyTablePanel.getCurrentTab();
    return tab == null ? null : tab.getKey();
  }

  @Override
  protected List<Property> getProperties(PropertiesContainer component) {
    PropertyTableTab tab = myPropertyTablePanel.getCurrentTab();
    if (tab != null) {
      return ((RadComponent)component).getProperties(tab.getKey());
    }
    return super.getProperties(component);
  }

  @Override
  protected boolean doRestoreDefault(ThrowableRunnable<Exception> runnable) {
    return myDesigner.getToolProvider().execute(runnable, DesignerBundle.message("designer.properties.restore_default"), false);
  }

  @Override
  protected boolean doSetValue(ThrowableRunnable<Exception> runnable) {
    return myDesigner.getToolProvider().execute(runnable, DesignerBundle.message("command.set.property.value"), false);
  }

  @Override
  protected PropertyContext getPropertyContext() {
    return myDesigner;
  }
}
