package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Factory;
import com.jetbrains.python.JythonManager;
import org.jetbrains.annotations.NotNull;
import org.python.core.PyList;
import org.python.core.PyObject;

/**
 * @author yole
 */
public class PythonPyInspectionToolProvider implements ApplicationComponent {
  public static PythonPyInspectionToolProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(PythonPyInspectionToolProvider.class);
  }

  private InspectionToolRegistrar myRegistrar;

  public PythonPyInspectionToolProvider(final InspectionToolRegistrar registrar) {
    myRegistrar = registrar;
  }

  @NotNull
  public String getComponentName() {
    return "PythonPyInspectionToolProvider";
  }

  public void initComponent() {
    JythonManager manager = JythonManager.getInstance();
    manager.execScriptFromResource("inspections/inspections.py");

    final PyList pyList = (PyList) manager.eval("getAllInspections()");
    int len = pyList.__len__();
    for(int i=0; i<len; i++) {
      final String inspectionToolName = pyList.__getitem__(i).toString();
      myRegistrar.registerInspectionToolFactory(new PyInspectionToolFactory(inspectionToolName));
    }
  }

  public void disposeComponent() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public static LocalInspectionTool createLocalInspectionTool(final String inspectionToolName) {
    final PyObject object = JythonManager.getInstance().eval(inspectionToolName + "()");
    final Object o = object.__tojava__(LocalInspectionTool.class);
    return (LocalInspectionTool)o;
  }

  private static class PyInspectionToolFactory implements Factory<InspectionTool> {
    private String myInspectionToolName;

    public PyInspectionToolFactory(final String inspectionToolName) {
      myInspectionToolName = inspectionToolName;
    }

    public InspectionTool create() {
      final LocalInspectionTool tool = createLocalInspectionTool(myInspectionToolName);
      return new LocalInspectionToolWrapper(tool);
    }
  }
}
