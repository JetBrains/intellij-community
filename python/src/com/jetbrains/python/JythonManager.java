package com.jetbrains.python;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.python.core.*;
import org.python.util.PythonInterpreter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * @author yole
 */
public class JythonManager {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.JythonManager");

  public static JythonManager getInstance() {
    return ServiceManager.getService(JythonManager.class);
  }

  private PythonInterpreter myInterpreter;

  public JythonManager() {
    @NonNls Properties postProperties = new Properties();
    postProperties.put("python.cachedir.skip", "true");
    String pythonScriptPath = findPythonScriptPath();
    postProperties.put("python.path", pythonScriptPath);
    LOG.info("Loading Python scripts from " + pythonScriptPath);
    PySystemState.initialize(PySystemState.getBaseProperties(), postProperties, new String[] { "" });
    myInterpreter = new PythonInterpreter();
  }

  private String findPythonScriptPath() {
    @NonNls String classRoot = PathUtil.getJarPathForClass(getClass());
    if (classRoot.endsWith(".jar")) {
      return classRoot;
    }
    // compiled classes
    File f = new File(new File(classRoot).getParent(), "python-py");
    if (f.exists()) {
      return f.getPath();
    }
    throw new RuntimeException("Can't figure out Python script path: " + classRoot);
  }

  public void execScriptFromResource(@NonNls String resourcePath) {
    final InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
    try {
      myInterpreter.execfile(stream);
    }
    finally {
      try {
        stream.close();
      }
      catch (IOException e) {
        LOG.error(e);
      }
    }
  }

  public PyObject eval(@NonNls final String code) {
    return myInterpreter.eval(code);
  }
}
