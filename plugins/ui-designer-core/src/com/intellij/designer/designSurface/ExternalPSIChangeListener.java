// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.designSurface;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ComparatorUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class ExternalPSIChangeListener extends PsiTreeChangeAdapter {
  private final Alarm myAlarm = new Alarm();
  protected final DesignerEditorPanel myDesigner;
  private final PsiFile myFile;
  private final int myDelayMillis;
  private final Runnable myRunnable;
  protected volatile boolean myRunState;
  private volatile boolean myInitialize;
  private String myContent;
  protected boolean myUpdateRenderer;
  private final Disposable myDisposable = Disposer.newDisposable();

  public ExternalPSIChangeListener(DesignerEditorPanel designer, PsiFile file, int delayMillis, Runnable runnable) {
    myDesigner = designer;
    myFile = file;
    myDelayMillis = delayMillis;
    myRunnable = runnable;
    myContent = myDesigner.getEditorText();
    PsiManager.getInstance(myDesigner.getProject()).addPsiTreeChangeListener(this, myDisposable);
  }

  public void setInitialize() {
    myInitialize = true;
  }

  public void start() {
    if (!myRunState) {
      myRunState = true;
    }
  }

  public void dispose() {
    Disposer.dispose(myDisposable);
    stop();
  }

  public void stop() {
    if (myRunState) {
      myRunState = false;
      clear();
    }
  }

  public void activate() {
    if (!myRunState) {
      start();
      if (!ComparatorUtil.equalsNullable(myContent, myDesigner.getEditorText()) || myDesigner.getRootComponent() == null) {
        myUpdateRenderer = false;
        addRequest();
      }
      myContent = null;
    }
  }

  public void deactivate() {
    if (myRunState) {
      stop();
      myContent = myDesigner.getEditorText();
    }

    myUpdateRenderer = false;
  }

  public void addRequest() {
    addRequest(myRunnable);
  }

  public boolean isActive() {
    return myRunState;
  }

  public boolean isUpdateRenderer() {
    return myUpdateRenderer;
  }

  public boolean ensureUpdateRenderer() {
    if (myRunState) {
      return myInitialize && !myDesigner.isProjectClosed();
    }
    myUpdateRenderer = true;
    return false;
  }

  public void addRequest(final Runnable runnable) {
    clear();
    myAlarm.addRequest(() -> {
      if (myRunState && myInitialize && !myDesigner.isProjectClosed()) {
        runnable.run();
      }
    }, myDelayMillis, ModalityState.stateForComponent(myDesigner));
  }

  public void clear() {
    myAlarm.cancelAllRequests();
  }

  protected void updatePsi(PsiTreeChangeEvent event) {
    if (myRunState && myFile == event.getFile()) {
      addRequest();
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // PSI
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    updatePsi(event);
  }
}