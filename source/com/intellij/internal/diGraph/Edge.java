package com.intellij.internal.diGraph;


/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: Mar 25, 2003
 * Time: 3:48:44 PM
 * To change this template use Options | File Templates.
 */

public interface Edge<NODE_TYPE extends Node> {
  NODE_TYPE beg();
  NODE_TYPE end();
}
