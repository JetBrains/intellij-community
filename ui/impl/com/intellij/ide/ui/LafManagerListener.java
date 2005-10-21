package com.intellij.ide.ui;

import java.util.EventListener;

/**
 * If you are interested in listening UI changes you have to
 * use this listener instead of registening <code>PropertyChangeListener</code>
 * into <code>UIManager</code>
 *
 * @author Vladimir Kondratyev
 */
public interface LafManagerListener extends EventListener{
  void lookAndFeelChanged(LafManager source);
}
