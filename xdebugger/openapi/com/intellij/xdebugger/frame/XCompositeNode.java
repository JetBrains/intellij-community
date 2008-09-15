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
   * Add an ellipsis node ("...") indicating that the node has too many children. If user double-click on that node
   * {@link XValueContainer#computeChildren(XCompositeNode)} method will be called again to add next children.
   * @param remaining number of remaining children or <code>-1</code> if unknown
   */
  void tooManyChildren(int remaining);

  /**
   * Indicates that an error occurs
   * @param errorMessage message desribing the error
   */
  void setErrorMessage(@NotNull String errorMessage);
}
