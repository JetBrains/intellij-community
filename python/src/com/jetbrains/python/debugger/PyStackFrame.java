// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.google.common.collect.Maps;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
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
import com.intellij.xdebugger.frame.XValue;
import com.intellij.xdebugger.frame.XValueChildrenList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.debugger.settings.PyDebuggerSettings;
import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.IntStream;

import static com.jetbrains.python.debugger.PyDebugValueGroupsKt.addGroupValues;

public class PyStackFrame extends XStackFrame {
  private static final Logger LOG = Logger.getInstance(PyStackFrame.class);

  private static final Object STACK_FRAME_EQUALITY_OBJECT = new Object();
  public static final String DOUBLE_UNDERSCORE = "__";
  @NotNull @NonNls public static final Set<String> HIDE_TYPES = Set.of("function", "type", "classobj", "module");
  public static final int DUNDER_VALUES_IND = 0;
  public static final int SPECIAL_TYPES_IND = DUNDER_VALUES_IND + 1;
  public static final int IPYTHON_VALUES_IND = SPECIAL_TYPES_IND + 1;
  public static final int NUMBER_OF_GROUPS = IPYTHON_VALUES_IND + 1;
  @NotNull @NonNls public static final Set<String> COMPREHENSION_NAMES = Set.of("<genexpr>", "<listcomp>", "<dictcomp>",
                                                                                         "<setcomp>");
  private final Project myProject;
  private final PyFrameAccessor myDebugProcess;
  private final PyStackFrameInfo myFrameInfo;
  private final XSourcePosition myPosition;

  private @Nullable Map<String, PyDebugValueDescriptor> myChildrenDescriptors;

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
    component.setIcon(AllIcons.Debugger.Frame);

    if (myPosition == null) {
      component.append(PyBundle.message("debugger.stack.frame.frame.not.available"), SimpleTextAttributes.GRAY_ATTRIBUTES);
      return;
    }

    final VirtualFile file = myPosition.getFile();
    boolean isExternal =
      ReadAction.compute(() -> {

        final Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null) {
          return !ProjectRootManager.getInstance(myProject).getFileIndex().isInContent(file);
        }
        else {
          return true;
        }
      });

    component.append(myFrameInfo.getName(), gray(isExternal));
    component.append(", ", gray(isExternal));
    component.append(myPosition.getFile().getName(), gray(isExternal));
    component.append(":", gray(isExternal));
    component.append(Integer.toString(myPosition.getLine() + 1), gray(isExternal));
  }

  protected static SimpleTextAttributes gray(boolean gray) {
    return (gray) ? SimpleTextAttributes.GRAYED_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES;
  }

  @Override
  public void computeChildren(@NotNull final XCompositeNode node) {
    if (node.isObsolete()) return;
    myDebugProcess.setCurrentRootNode(node);
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        boolean cached = myDebugProcess.isFrameCached(this);
        XValueChildrenList values = myDebugProcess.loadFrame(this);
        if (!node.isObsolete()) {
          addChildren(node, values);
        }
        if (values != null && !cached) {
          PyDebugValue.getAsyncValues(this, myDebugProcess, values);
        }
      }
      catch (PyDebuggerException e) {
        if (!node.isObsolete()) {
          node.setErrorMessage(PyBundle.message("debugger.stack.frame.unable.to.display.frame.variables"));
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
    IntStream.range(0, NUMBER_OF_GROUPS).mapToObj(i -> new HashMap<String, XValue>()).forEach(specialValuesGroups::add);
    boolean isSpecialEmpty = true;

    for (int i = 0; i < children.size(); i++) {
      XValue value = children.getValue(i);
      String name = children.getName(i);
      if (value instanceof PyDebugValue) {
        PyDebugValue pyValue = (PyDebugValue)value;

        restoreValueDescriptor(pyValue);

        if (pyValue.isReturnedVal() && debuggerSettings.isWatchReturnValues()) {
          returnedValues.put(name, value);
        }
        else if (!debuggerSettings.isSimplifiedView()) {
          filteredChildren.add(name, value);
        }
        else {
          int groupIndex = -1;
          if (name.startsWith(DOUBLE_UNDERSCORE) && (name.endsWith(DOUBLE_UNDERSCORE)) && name.length() > 4 &&
              !name.equals("__exception__")) {
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
      addGroupValues(PyBundle.message("debugger.stack.frame.return.values"), AllIcons.Debugger.WatchLastReturnValue, node, returnedValues, "()");
    }
    if (!isSpecialEmpty) {
      final Map<String, XValue> specialElements = mergeSpecialGroupElementsOrdered(specialValuesGroups);
      addGroupValues(PyBundle.message("debugger.stack.frame.special.variables"), PythonIcons.Python.Debug.SpecialVar, node, specialElements, null);
    }
  }

  private static Map<String, XValue> mergeSpecialGroupElementsOrdered(List<Map<String, XValue>> specialValuesGroups) {
    final LinkedHashMap<String, XValue> result = new LinkedHashMap<>();
    for (Map<String, XValue> group : specialValuesGroups) {
      for (Map.Entry<String, XValue> entry : group.entrySet()) {
        result.put(entry.getKey(), entry.getValue());
      }
    }
    return result;
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

  @NotNull
  public String getName() {
    return myFrameInfo.getName();
  }

  public boolean isComprehension() {
    return COMPREHENSION_NAMES.contains(getName());
  }

  public void setChildrenDescriptors(@Nullable Map<String, PyDebugValueDescriptor> childrenDescriptors) {
    myChildrenDescriptors = childrenDescriptors;
  }

  public void restoreChildrenDescriptors(@NotNull Map<String, Map<String, PyDebugValueDescriptor>> descriptorsCache) {
    final String threadFrameId = getThreadFrameId();
    final Map<String, PyDebugValueDescriptor> childrenDescriptors = descriptorsCache.getOrDefault(threadFrameId, Maps.newHashMap());
    setChildrenDescriptors(childrenDescriptors);
    descriptorsCache.put(threadFrameId, childrenDescriptors);
  }

  private void restoreValueDescriptor(PyDebugValue value) {
    if (myChildrenDescriptors != null) {
      PyDebugValueDescriptor descriptor = myChildrenDescriptors.getOrDefault(value.getName(), null);
      if (descriptor == null) {
        descriptor = new PyDebugValueDescriptor();
        myChildrenDescriptors.put(value.getName(), descriptor);
      }
      value.setDescriptor(descriptor);
    }
  }
}
