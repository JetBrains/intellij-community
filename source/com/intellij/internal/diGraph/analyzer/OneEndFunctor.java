package com.intellij.internal.diGraph.analyzer;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 21.06.2003
 * Time: 22:32:42
 * To change this template use Options | File Templates.
 */
public interface OneEndFunctor {
  Mark compute(Mark from, Mark edge, Mark to);
}
