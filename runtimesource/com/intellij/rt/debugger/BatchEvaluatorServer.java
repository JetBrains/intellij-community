/*
 * @author: Eugene Zhuravlev
 * Date: Sep 16, 2002
 * Time: 10:56:58 PM
 */
package com.intellij.rt.debugger;

public class BatchEvaluatorServer {
  Object[] myObjects;

  public Object[] evaluate(Object[] objects) {
    myObjects = objects;
    Object[] result = new Object[objects.length];
    for (int idx = 0; idx < myObjects.length; idx++) {
      try {
        result[idx] = myObjects[idx].toString();
      }
      catch (Throwable e) {
        result[idx] = e;
      }
    }
    return result;
  }
}
