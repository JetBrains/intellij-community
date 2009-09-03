package org.jetbrains.yaml;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;

/**
 * @author oleg
 */
public class YAMLElementType extends IElementType {
  public YAMLElementType(@NonNls String debugName) {
    super(debugName, YAMLFileType.YML.getLanguage());
  }
}
