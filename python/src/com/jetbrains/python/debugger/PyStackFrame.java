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

import com.google.common.collect.Lists;
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
import com.intellij.util.containers.HashSet;
import com.intellij.xdebugger.XSourcePosition;
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator;
import com.intellij.xdebugger.frame.*;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.stream.IntStream;


public class PyStackFrame extends XStackFrame {

  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.pydev.PyStackFrame");

  private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();
  public static final String DOUBLE_UNDERSCORE = "__";
  public static final String RETURN_VALUES_GROUP_NAME = "Return Values";
  public static final String SPECIAL_VARIABLES_GROUP_NAME = "Special Variables";
  public static final HashSet<String> HIDE_TYPES = new HashSet<>(Arrays.asList("function", "type", "classobj", "module"));
  public static final int DUNDER_VALUES_IND = 0;
  public static final int SPECIAL_TYPES_IND = DUNDER_VALUES_IND + 1;
  public static final int IPYTHON_VALUES_IND = SPECIAL_TYPES_IND + 1;
  public static final int NUMBER_OF_GROUPS = IPYTHON_VALUES_IND + 1;

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
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        boolean cached = myDebugProcess.isCurrentFrameCached();
        XValueChildrenList values = myDebugProcess.loadFrame();
        if (!node.isObsolete()) {
          addChildren(node, values);
        }
        if (values != null && !cached) {
          PyDebugValue.getAsyncValues(myDebugProcess, values);
        }
      }
      catch (PyDebuggerException e) {
        if (!node.isObsolete()) {
          node.setErrorMessage("Unable to display frame variables");
        }
        LOG.warn(e);
      }
    });
  }

  protected void addChildren(@NotNull final XCompositeNode node, @Nullable final XValueChildrenList children) {
    if (children == null) {
      node.addChildren(XValueChildrenList.EMPTY, true);
      return;
    }
    final PyDebuggerSettings debuggerSettings = PyDebuggerSettings.getInstance();
    final XValueChildrenList filteredChildren = new XValueChildrenList();
    final HashMap<String, XValue> returnedValues = new HashMap<>();
    final ArrayList<Map<String, XValue>> specialValuesGroups = new ArrayList<>();
    IntStream.range(0, NUMBER_OF_GROUPS).mapToObj(i -> new HashMap()).forEach(specialValuesGroups::add);
    boolean isSpecialEmpty = true;

    for (int i = 0; i < children.size(); i++) {
      XValue value = children.getValue(i);
      String name = children.getName(i);
      if (value instanceof PyDebugValue) {
        PyDebugValue pyValue = (PyDebugValue)value;
        if (pyValue.isReturnedVal() && debuggerSettings.isWatchReturnValues()) {
          returnedValues.put(name, value);
        }
        else if (!debuggerSettings.isSimplifiedView()) {
          filteredChildren.add(name, value);
        }
        else {
          int groupIndex = -1;
          if (name.startsWith(DOUBLE_UNDERSCORE) && (name.endsWith(DOUBLE_UNDERSCORE)) && name.length() > 4) {
            groupIndex = DUNDER_VALUES_IND;
          }
          else if (pyValue.isIPythonHidden()) {
            groupIndex = IPYTHON_VALUES_IND;
          }
          else if (HIDE_TYPES.contains(pyValue.getType())) {
            groupIndex = SPECIAL_TYPES_IND;
          }
          if (groupIndex > -1) {
            specialValuesGroups.get(groupIndex).put(name, value);
            isSpecialEmpty = false;
          }
          else {
            filteredChildren.add(name, value);
          }
        }
      }
    }
    node.addChildren(filteredChildren, returnedValues.isEmpty() && isSpecialEmpty);
    if (!returnedValues.isEmpty()) {
      addReturnedValuesGroup(node, returnedValues);
    }
    if (!isSpecialEmpty) {
      addSpecialValuesGroup(node, specialValuesGroups);
    }
  }

  private static void addReturnedValuesGroup(@NotNull final XCompositeNode node, Map<String, XValue> returnedValues) {
    final ArrayList<XValueGroup> group = Lists.newArrayList();
    group.add(new XValueGroup(RETURN_VALUES_GROUP_NAME) {
      @Override
      public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList list = new XValueChildrenList();
        for (Map.Entry<String, XValue> entry : returnedValues.entrySet()) {
          list.add(entry.getKey() + "()", entry.getValue());
        }
        node.addChildren(list, true);
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return AllIcons.Debugger.WatchLastReturnValue;
      }
    });
    node.addChildren(XValueChildrenList.topGroups(group), true);
  }

  private static void addSpecialValuesGroup(@NotNull final XCompositeNode node, List<Map<String, XValue>> specialValuesGroups) {
    final ArrayList<XValueGroup> group = Lists.newArrayList();
    group.add(new XValueGroup(SPECIAL_VARIABLES_GROUP_NAME) {
      @Override
      public void computeChildren(@NotNull XCompositeNode node) {
        XValueChildrenList list = new XValueChildrenList();
        for (Map<String, XValue> group : specialValuesGroups) {
          for (Map.Entry<String, XValue> entry : group.entrySet()) {
            list.add(entry.getKey(), entry.getValue());
          }
        }
        node.addChildren(list, true);
      }

      @Nullable
      @Override
      public Icon getIcon() {
        return PythonIcons.Python.Debug.SpecialVar;
      }
    });
    node.addChildren(XValueChildrenList.topGroups(group), true);
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

  public String getFrameName() {
    return myFrameInfo.getName();
  }

  protected XSourcePosition getPosition() {
    return myPosition;
  }
}
