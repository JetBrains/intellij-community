package com.intellij.debugger.actions;

import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.tree.ValueDescriptor;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.sun.jdi.*;

import java.util.List;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class JumpToObjectAction extends DebuggerAction{
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);

    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode == null) return;

    final NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
    if(!(descriptor instanceof ValueDescriptor)) return;

    DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) return;

    debugProcess.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
      public void contextAction() throws Exception {
        final SourcePosition sourcePosition = calcPosition((ValueDescriptor)descriptor, debugProcess);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, sourcePosition.getFile().getVirtualFile(), sourcePosition.getOffset()), true);
          }
        });
      }
    });


  }

  public void update(final AnActionEvent e) {
    if(!isFirstStart(e)) return;

    DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
    if(debugProcess == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode == null) {
      e.getPresentation().setVisible(false);
      return;
    }

    final NodeDescriptorImpl descriptor = selectedNode.getDescriptor();

    if(!(descriptor instanceof ValueDescriptor)) {
      e.getPresentation().setVisible(false);
      return;
    }

    e.getPresentation().setVisible(true);
    e.getPresentation().setEnabled(true);

    debugProcess.getManagerThread().invokeLater(new SuspendContextCommandImpl(debuggerContext.getSuspendContext()) {
      public void contextAction() throws Exception {
        SourcePosition sourcePosition = calcPosition((ValueDescriptor)descriptor, debugProcess);
        if(sourcePosition != null) {
          enableAction(e, true);
        }
      }
    });
  }

  private SourcePosition calcPosition(final ValueDescriptor descriptor, final DebugProcessImpl debugProcess)
    throws ClassNotLoadedException, AbsentInformationException {
    Value value = descriptor.getValue();
    if(value != null) {
      Type type = value.type();
      if(type != null) {
        if(type instanceof ArrayType) {
          type = ((ArrayType)type).componentType();
        }

        if(type instanceof ClassType) {
          List<Location> locations = ((ClassType)type).allLineLocations();
          if(locations.size() > 0) {
            final Location location = locations.get(0);
            return ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
              public SourcePosition compute() {
                return debugProcess.getPositionManager().getSourcePosition(location);
              }
            });
          }
        }
      }
    }

    return null;
  }
}
