// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.designer.propertyTable;

import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.designer.DesignerBundle;
import com.intellij.designer.designSurface.ComponentSelectionListener;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.*;
import com.intellij.ide.CopyProvider;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.util.Collections;
import java.util.List;

public class RadPropertyTable extends PropertyTable implements UiDataProvider, ComponentSelectionListener {
  private final MyCopyProvider myCopyProvider = new MyCopyProvider();

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
  public void uiDataSnapshot(@NotNull DataSink sink) {
    if (myDesigner == null) return;
    sink.set(PlatformCoreDataKeys.FILE_EDITOR, myDesigner.getEditor());
    sink.set(PlatformDataKeys.COPY_PROVIDER, isEditing() ? null : myCopyProvider);
  }

  @Override
  protected List<ErrorInfo> getErrors(@NotNull PropertiesContainer container) {
    return container instanceof RadComponent ? RadComponent.getError((RadComponent)container) : Collections.emptyList();
  }

  @Override
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
        update(Collections.emptyList(), null);
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

  private class MyCopyProvider implements CopyProvider {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      copySelectedProperty();
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return getSelectionProperty() != null;
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }
  }

  private void copySelectedProperty() {
    try {
      Property property = getSelectionProperty();
      Object value = getValue(property);
      Transferable transferable;

      if (value == null) {
        transferable = new TextTransferable("");
      }
      else {
        transferable = property.doCopy(myContainers.get(0), value);
      }

      CopyPasteManager.getInstance().setContents(transferable);
    }
    catch (Throwable e) {
      myDesigner.showError(DesignerBundle.message("designer.copy.property.error"), e);
    }
  }
}