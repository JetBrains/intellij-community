package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.ArrayUtil;
import org.jdom.Element;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;

/**
 * @author max
 */
public class InspectionToolRegistrar implements ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.InspectionToolRegistrar");

  private final ArrayList<Class> myInspectionTools;
  private final ArrayList<Class> myLocalInspectionTools;

  public InspectionToolRegistrar(InspectionToolProvider[] providers) {
    myInspectionTools = new ArrayList<Class>();
    myLocalInspectionTools = new ArrayList<Class>();

    for (int i = 0; i < providers.length; i++) {
      InspectionToolProvider provider = providers[i];
      Class[] classes = provider.getInspectionClasses();
      for (int j = 0; j < classes.length; j++) {
        if (LocalInspectionTool.class.isAssignableFrom(classes[j])) {
          registerLocalInspection(classes[j]);
        }
        else {
          registerInspectionTool(classes[j]);
        }
      }
    }
  }

  public static InspectionToolRegistrar getInstance() {
    return ApplicationManager.getApplication().getComponent(InspectionToolRegistrar.class);
  }

  public String getComponentName() {
    return "InspectionToolRegistrar";
  }

  public void readExternal(Element element) throws InvalidDataException {
  }

  public void writeExternal(Element element) throws WriteExternalException {
  }

  public void disposeComponent() {
  }

  public void initComponent() {
  }

  public void registerLocalInspection(Class toolClass) {
    myLocalInspectionTools.add(toolClass);
  }

  public void registerInspectionTool(Class toolClass) {
    if (myInspectionTools.contains(toolClass)) return;
    myInspectionTools.add(toolClass);
  }

  public InspectionTool[] createTools(Project project) {
    int ordinaryToolsSize = myInspectionTools.size();
    InspectionTool[] tools = new InspectionTool[ordinaryToolsSize + myLocalInspectionTools.size()];
    for (int i = 0; i < tools.length; i++) {
      tools[i] = i < ordinaryToolsSize
                 ? instantiateTool(myInspectionTools.get(i), project)
                 : new LocalInspectionToolWrapper(instantiateLocalTool(myLocalInspectionTools.get(i - ordinaryToolsSize)));
    }

    return tools;
  }

  private LocalInspectionTool instantiateLocalTool(Class toolClass) {
    try {
      Constructor constructor;
      Object[] args;
      constructor = toolClass.getDeclaredConstructor(new Class[0]);
      args = ArrayUtil.EMPTY_OBJECT_ARRAY;
      return (LocalInspectionTool) constructor.newInstance(args);
    } catch (NoSuchMethodException e) {
      LOG.error(e);
    } catch (SecurityException e) {
      LOG.error(e);
    } catch (InstantiationException e) {
      LOG.error(e);
    } catch (IllegalAccessException e) {
      LOG.error(e);
    } catch (IllegalArgumentException e) {
      LOG.error(e);
    } catch (InvocationTargetException e) {
      LOG.error(e);
    }
    return null;
  }

  private InspectionTool instantiateTool(Class toolClass, Project project) {
    try {
      Constructor constructor;
      Object[] args;
      try {
        constructor = toolClass.getDeclaredConstructor(new Class[]{Project.class});
        args = new Object[]{project};
      }
      catch (NoSuchMethodException e) {
        constructor = toolClass.getDeclaredConstructor(new Class[0]);
        args = ArrayUtil.EMPTY_OBJECT_ARRAY;
      }

      constructor.setAccessible(true);
      return (InspectionTool) constructor.newInstance(args);
    } catch (SecurityException e) {
      LOG.error(e);
    } catch (NoSuchMethodException e) {
      LOG.error(e);
    } catch (InstantiationException e) {
      LOG.error(e);
    } catch (IllegalAccessException e) {
      LOG.error(e);
    } catch (IllegalArgumentException e) {
      LOG.error(e);
    } catch (InvocationTargetException e) {
      LOG.error(e);
    }

    return null;
  }

  public LocalInspectionTool[] createLocalTools() {
    LocalInspectionTool[] tools = new LocalInspectionTool[myLocalInspectionTools.size()];
    for (int i = 0; i < tools.length; i++) {
      tools[i] = instantiateLocalTool(myLocalInspectionTools.get(i));
    }

    return tools;
  }
}
