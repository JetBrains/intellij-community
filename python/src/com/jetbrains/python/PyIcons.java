package com.jetbrains.python;

import icons.PythonIcons;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Preloads python-specific icons.
 * User: dcheryasov
 * Date: Oct 29, 2008
 */
public interface PyIcons {
  @NonNls String DATA_ROOT = "icons/"; /*"/com/jetbrains/python/PyIcons";*/

  Icon PRIVATE = PythonIcons.Python.Icons.Nodes.Lock;
  Icon PREDEFINED = PythonIcons.Python.Icons.Nodes.Cyan_dot;
  Icon INVISIBLE = PythonIcons.Python.Icons.Nodes.Red_inv_triangle;
}
