package com.intellij.ide.structureView.impl;

import com.intellij.ide.SelectInManager;
import com.intellij.ide.impl.StructureViewSelectInTarget;
import com.intellij.ide.impl.StructureViewWrapper;
import com.intellij.ide.structureView.StructureView;
import com.intellij.ide.structureView.StructureViewExtension;
import com.intellij.ide.structureView.StructureViewFactory;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Eugene Belyaev
 */
public final class StructureViewFactoryImpl extends StructureViewFactory implements JDOMExternalizable, ProjectComponent {
  public boolean AUTOSCROLL_MODE = true;
  public boolean AUTOSCROLL_FROM_SOURCE = false;

  private Project myProject;
  private StructureViewWrapper myStructureViewWrapper;

  private final MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension> myExtensions = new MultiValuesMap<Class<? extends PsiElement>, StructureViewExtension>();

  public StructureViewFactoryImpl(Project project) {
    myProject = project;
  }

  public StructureView getStructureView() {
    return myStructureViewWrapper;
  }

  public void disposeComponent() {
  }

  public void initComponent() { }

  public void projectOpened() {
    myStructureViewWrapper = new StructureViewWrapper(myProject);
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run(){
        ToolWindowManager toolWindowManager=ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow=toolWindowManager.registerToolWindow(ToolWindowId.STRUCTURE_VIEW,myStructureViewWrapper.getComponent(),ToolWindowAnchor.LEFT);
        toolWindow.setIcon(IconLoader.getIcon("/general/toolWindowStructure.png"));
        SelectInManager.getInstance(myProject).addTarget(new StructureViewSelectInTarget(myProject));
      }
    });
  }

  public void projectClosed() {
    ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.STRUCTURE_VIEW);
    myStructureViewWrapper.dispose();
    myStructureViewWrapper=null;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public String getComponentName() {
    return "StructureViewFactory";
  }


  public void registerExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.put(type, extension);
  }

  public void unregisterExtension(Class<? extends PsiElement> type, StructureViewExtension extension) {
    myExtensions.remove(type, extension);
  }

  public List<StructureViewExtension> getAllExtensions(Class<? extends PsiElement> type) {
    ArrayList<StructureViewExtension> result = new ArrayList<StructureViewExtension>();

    for (Iterator<Class<? extends PsiElement>> iterator = myExtensions.keySet().iterator(); iterator.hasNext();) {
      Class<? extends PsiElement> registeregType = iterator.next();
      if (registeregType.isAssignableFrom(type)) result.addAll(myExtensions.get(registeregType));
    }
    return result;
  }
}