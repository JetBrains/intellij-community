/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.graph;

import java.util.Iterator;
import java.util.Collection;

/**
 *  @author dsl
 */
public interface Graph<Node> {
  Collection<Node> getNodes();

  Iterator<Node> getIn(Node n);

  Iterator<Node> getOut(Node n);
}
