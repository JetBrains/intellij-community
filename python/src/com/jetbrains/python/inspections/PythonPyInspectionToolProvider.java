package com.jetbrains.python.inspections;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.InspectionToolsFactory;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.Function;
import com.jetbrains.python.JythonManager;
import org.jetbrains.annotations.Nullable;
import org.python.core.PyList;
import org.python.core.PyObject;

/**
 * @author yole
 */
public class PythonPyInspectionToolProvider implements InspectionToolsFactory {
  public static PythonPyInspectionToolProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(PythonPyInspectionToolProvider.class);
  }

  private final InspectionToolRegistrar myRegistrar;

  public PythonPyInspectionToolProvider(final InspectionToolRegistrar registrar) {
    myRegistrar = registrar;
  }

  @Nullable
  public static LocalInspectionTool createLocalInspectionTool(final String inspectionToolName) {
    try {
      final PyObject object = JythonManager.getInstance().eval(inspectionToolName + "()");
      final Object o = object.__tojava__(LocalInspectionTool.class);
      return (LocalInspectionTool)o;
    }
    catch (Exception e) {
      return null;
    }
  }

  public InspectionProfileEntry[] createTools() {
    myRegistrar.registerInspectionToolProvider(new Function<String, InspectionTool>() {
      @Nullable
      public InspectionTool fun(String shortName) {
        final LocalInspectionTool tool = createLocalInspectionTool(shortName);
        return tool != null ? new LocalInspectionToolWrapper(tool) : null;
      }
    });

    final JythonManager manager = JythonManager.getInstance();
    manager.execScriptFromResource("inspections/inspections.py");

    final PyList pyList = (PyList) manager.eval("getAllInspections()");
    int len = pyList.__len__();
    final InspectionProfileEntry[] profileEntries = new InspectionProfileEntry[len];
    for(int i=0; i<len; i++) {
      final String inspectionToolName = pyList.__getitem__(i).toString();
      profileEntries[i] = createLocalInspectionTool(inspectionToolName);
    }
    return profileEntries;
  }
}
