package com.intellij.xdebugger.frame;

import java.util.List;

/**
 * @author nik
 */
public interface XCompositeNode {

  void setChildren(List<XValue> children);

}
