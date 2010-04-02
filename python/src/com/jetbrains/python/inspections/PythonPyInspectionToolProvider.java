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

import java.util.HashSet;
import java.util.Set;

/**
 * @author yole
 */
public class PythonPyInspectionToolProvider implements InspectionToolsFactory {
  private final JythonManager myManager;

  public static PythonPyInspectionToolProvider getInstance() {
    return ApplicationManager.getApplication().getComponent(PythonPyInspectionToolProvider.class);
  }

  private final InspectionToolRegistrar myRegistrar;

  private final Set<String> myShortNames = new HashSet<String>();

  public PythonPyInspectionToolProvider(final InspectionToolRegistrar registrar, final JythonManager jythonManager) {
    myRegistrar = registrar;
    myManager = jythonManager;
  }

  @Nullable
  public LocalInspectionTool createLocalInspectionTool(final String inspectionToolName) {
    try {
      final PyObject object = myManager.eval(inspectionToolName + "()");
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
        final LocalInspectionTool tool = myShortNames.contains(shortName) ? createLocalInspectionTool(shortName) : null;
        return tool != null ? new LocalInspectionToolWrapper(tool) : null;
      }
    });

    myManager.execScriptFromResource("inspections/inspections.py");

    final PyList pyList = (PyList) myManager.eval("getAllInspections()");
    int len = pyList.__len__();
    final InspectionProfileEntry[] profileEntries = new InspectionProfileEntry[len];
    for(int i=0; i<len; i++) {
      final String inspectionToolName = pyList.__getitem__(i).toString();
      profileEntries[i] = createLocalInspectionTool(inspectionToolName);
      myShortNames.add(inspectionToolName);
    }
    return profileEntries;
  }
}
