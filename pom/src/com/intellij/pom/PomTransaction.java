package com.intellij.pom;

import com.intellij.pom.event.PomModelEvent;
import com.intellij.util.IncorrectOperationException;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: Oct 6, 2004
 * Time: 9:48:15 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PomTransaction{
  PomModelEvent run() throws IncorrectOperationException;
}
