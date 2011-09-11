package de.plushnikov.intellij.plugin;

import com.intellij.psi.impl.source.tree.ChangeUtil;
import de.plushnikov.intellij.lombok.psi.MyLightMethodTreeGenerator;

/**
 * Main application component, that loads Lombok support
 */
public class LombokLoader {
  public LombokLoader() {
    ChangeUtil.registerTreeGenerator(new MyLightMethodTreeGenerator());
  }
}
