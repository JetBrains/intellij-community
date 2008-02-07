package com.intellij.xdebugger.impl.ui.tree;

import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author nik
 */
public interface XDebuggerTreeListener {

  void nodeLoaded(@NotNull XValueNodeImpl node, final String name, final String value);

  void childrenLoaded(@NotNull XValueContainerNode<?> node, @NotNull List<XValueContainerNode<?>> children);

}
