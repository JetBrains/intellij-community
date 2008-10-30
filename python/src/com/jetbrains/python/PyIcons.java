package com.jetbrains.python;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Preloads python-specific icons.
 * User: dcheryasov
 * Date: Oct 29, 2008
 */
public interface PyIcons {
  @NonNls String DATA_ROOT = "icons/"; /*"/com/jetbrains/python/PyIcons";*/

  Icon PRIVATE = IconLoader.getIcon(DATA_ROOT + "nodes/lock.png");
  Icon PREDEFINED = IconLoader.getIcon(DATA_ROOT + "nodes/cyan-dot.png");
  Icon INVISIBLE = IconLoader.getIcon(DATA_ROOT + "nodes/red-inv-triangle.png");
}
