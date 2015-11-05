/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.debugger;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredTextContainer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.XCompositeNode;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.frame.XValueChildrenList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class PyStackFrame extends XStackFrame {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyStackFrame");

  private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();

  private Project myProject;
  private final PyFrameAccessor myDebugProcess;
  private final PyStackFrameInfo myFrameInfo;
  private final XSourcePosition myPosition;

  public PyStackFrame(@NotNull Project project,
                      @NotNull final PyFrameAccessor debugProcess,
                      @NotNull final PyStackFrameInfo frameInfo, XSourcePosition position) {
    myProject = project;
    myDebugProcess = debugProcess;
    myFrameInfo = frameInfo;
    myPosition = position;
  }

  @Override
  public Object getEqualityObject() {
    return STACK_FRAME_EQUALITY_OBJECT;
  }

  @Override
  public XSourcePosition getSourcePosition() {
    return myPosition;
  }

  @Override
  public XDebuggerEvaluator getEvaluator() {
    return new PyDebuggerEvaluator(myProject, myDebugProcess);
  }

  @Override
  public void customizePresentation(@NotNull ColoredTextContainer component) {
    component.setIcon(AllIcons.Debugger.StackFrame);

    if (myPosition == null) {
      component.append("<frame not available>", SimpleTextAttributes.GRAY_ATTRIBUTES);
      return;
    }

    boolean isExternal = true;
    final VirtualFile file = myPosition.getFile();
    AccessToken lock = ApplicationManager.getApplication().acquireReadActionLock();
    try {
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      if (document != null) {
        isExternal = !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file);
      }
    }
    finally {
      lock.finish();
    }

    component.append(myFrameInfo.getName(), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(", ", gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(myPosition.getFile().getName(), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(":", gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
    component.append(Integer.toString(myPosition.getLine() + 1), gray(SimpleTextAttributes.REGULAR_ATTRIBUTES, isExternal));
  }

  private static SimpleTextAttributes gray(SimpleTextAttributes attributes, boolean gray) {
    if (!gray) {
      return attributes;
    }
    else {
      return getGrayAttributes(attributes);
    }
  }

  protected static SimpleTextAttributes getGrayAttributes(SimpleTextAttributes attributes) {
    return (attributes.getStyle() & SimpleTextAttributes.STYLE_ITALIC) != 0
           ? SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES : SimpleTextAttributes.GRAYED_ATTRIBUTES;
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          XValueChildrenList values = myDebugProcess.loadFrame();
          if (!node.isObsolete()) {
            addChildren(node, values);
          }
        }
        catch (PyDebuggerException e) {
          if (!node.isObsolete()) {
            node.setErrorMessage("Unable to display frame variables");
          }
          LOG.warn(e);
        }
      }
    });
  }

  protected void addChildren(@NotNull final XCompositeNode node, @Nullable final XValueChildrenList children) {
    node.addChildren(children != null ? children : XValueChildrenList.EMPTY, true);
  }

  public String getThreadId() {
    return myFrameInfo.getThreadId();
  }

  public String getFrameId() {
    return myFrameInfo.getId();
  }

  public String getThreadFrameId() {
    return myFrameInfo.getThreadId() + ":" + myFrameInfo.getId();
  }

  protected XSourcePosition getPosition() {
    return myPosition;
  }
}
