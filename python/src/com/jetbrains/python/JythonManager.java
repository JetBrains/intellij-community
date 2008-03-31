package com.jetbrains.python;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.python.core.PyObject;
import org.python.core.PySystemState;
import org.python.util.PythonInterpreter;

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
    PySystemState.initialize(PySystemState.getBaseProperties(), postProperties, new String[] { "" });
    myInterpreter = new PythonInterpreter();
  }

  public void execScriptFromResource(@NonNls String resourcePath) {
    final InputStream stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
    if (stream != null) { // TODO: stream == null seems to indicate some build problem?
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
  }

  public PyObject eval(@NonNls final String code) {
    return myInterpreter.eval(code);
  }
}
