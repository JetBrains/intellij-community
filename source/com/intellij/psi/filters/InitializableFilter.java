package com.intellij.psi.filters;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.04.2003
 * Time: 16:15:21
 * To change this template use Options | File Templates.
 */
public interface InitializableFilter extends ElementFilter{
  void init(Object[] fromGetter);
}
