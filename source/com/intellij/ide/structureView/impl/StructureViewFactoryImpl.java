package com.intellij.ide.structureView.impl;

import com.intellij.ide.impl.StructureViewWrapperImpl;
import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactoryEx;
import com.intellij.ide.structureView.StructureViewWrapper;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ReflectionCache;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

/**
 * @author Eugene Belyaev
 */
public final class StructureViewFactoryImpl extends StructureViewFactoryEx implements JDOMExternalizable, ProjectComponent {
  @SuppressWarnings({"WeakerAccess"}) public boolean AUTOSCROLL_MODE = true;
  @SuppressWarnings({"WeakerAccess"}) public boolean AUTOSCROLL_FROM_SOURCE = false;
  @SuppressWarnings({"WeakerAccess"}) public String ACTIVE_ACTIONS = "";

  private Project myProject;
  private StructureViewWrapperImpl myStructureViewWrapperImpl;

  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();
  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myImplExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();

  public StructureViewFactoryImpl(Project project) {
    myProject = project;
  }

  public StructureViewWrapper getStructureViewWrapper() {
    return myStructureViewWrapperImpl;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    myStructureViewWrapperImpl = new StructureViewWrapperImpl(myProject);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run(){
        ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow=toolWindowManager.registerToolWindow(ToolWindowId.STRUCTURE_VIEW,myStructureViewWrapperImpl.getComponent(),ToolWindowAnchor.LEFT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowStructure.png"));
      }
    });
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.STRUCTURE_VIEW);
    myStructureViewWrapperImpl.dispose();
    myStructureViewWrapperImpl=null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  @NotNull
  public String getComponentName() {
    return "StructureViewFactory";
  }


  public void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.put(type, extension);
    myImplExtensions.clear();
  }

  public void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.remove(type, extension);
    myImplExtensions.clear();
  }

  public Collection<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type) {
    Collection<StructureViewExtension> result = myImplExtensions.get(type);
    if (result == null) {
      for (Class<? extends PsiElement> registeredType : myExtensions.keySet()) {
        if (ReflectionCache.isAssignable(registeredType, type)) {
          final Collection<StructureViewExtension> extensions = myExtensions.get(registeredType);
          for (StructureViewExtension extension : extensions) {
            myImplExtensions.put(type, extension);
          }
        }
      }
      result = myImplExtensions.get(type);
      if (result == null) return Collections.emptyList();
    }
    return result;
  }

  public void setActiveAction(final String name, final boolean state) {
    Collection<String> activeActions = collectActiveActions();

    if (state) {
      activeActions.add(name);
    }
    else {
      activeActions.remove(name);
    }

    ACTIVE_ACTIONS = toString(activeActions);
  }

  private static String toString(final Collection<String> activeActions) {
    return StringUtil.join(activeActions, ",");
  }

  private Collection<String> collectActiveActions() {
    final String[] strings = ACTIVE_ACTIONS.split(",");
    return new HashSet<String>(Arrays.asList(strings));
  }

  public boolean isActionActive(final String name) {
    return collectActiveActions().contains(name);
  }
}