package com.jetbrains.python;

import com.intellij.openapi.extensions.ExtensionFactory;
import com.intellij.openapi.diagnostic.Logger;
import org.python.core.PyObject;

/**
 * @author yole
 */
public class PyExtensionFactory implements ExtensionFactory {
  private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.PyExtensionFactory");

  public Object createInstance(final String factoryArgument, final String implementationClass) {
    LOG.info("Instantiating Jython class " + implementationClass + " from script " + factoryArgument);
    JythonManager jythonManager = JythonManager.getInstance();
    jythonManager.execScriptFromResource(factoryArgument);
    final PyObject object = jythonManager.eval(implementationClass + "()");
    return object.__tojava__(Object.class);
  }
}
