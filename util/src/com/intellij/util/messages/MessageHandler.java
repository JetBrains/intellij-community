/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 1, 2006
 * Time: 11:49:58 PM
 */
package com.intellij.util.messages;

import java.lang.reflect.Method;

public interface MessageHandler {
  void handle(Method event, Object... params);
}