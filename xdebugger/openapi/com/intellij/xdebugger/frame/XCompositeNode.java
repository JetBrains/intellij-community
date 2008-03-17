package com.intellij.xdebugger.frame;

import com.intellij.xdebugger.Obsolescent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface XCompositeNode extends Obsolescent {
  /**
   * Add children to the node.
   * @param children child nodes to add
   * @param last <code>true</code> if all children added
   */
  void addChildren(List<XValue> children, final boolean last);

  /**
   * Indicates that an error occurs
   * @param errorMessage message desribing the error
   */
  void setErrorMessage(@NotNull String errorMessage);
}
