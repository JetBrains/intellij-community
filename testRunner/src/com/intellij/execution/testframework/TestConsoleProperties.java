/*
 * User: anna
 * Date: 25-May-2007
 */
package com.intellij.execution.testframework;

import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.openapi.project.Project;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.Storage;

import java.util.ArrayList;
import java.util.HashMap;

public abstract class TestConsoleProperties extends StoringPropertyContainer {
  public static final BooleanProperty SCROLL_TO_STACK_TRACE = new BooleanProperty("scrollToStackTrace", false);
  public static final BooleanProperty SELECT_FIRST_DEFECT = new BooleanProperty("selectFirtsDefect", false);
  public static final BooleanProperty TRACK_RUNNING_TEST = new BooleanProperty("trackRunningTest", true);
  public static final BooleanProperty HIDE_PASSED_TESTS = new BooleanProperty("hidePassedTests", true);
  public static final BooleanProperty SCROLL_TO_SOURCE = new BooleanProperty("scrollToSource", false);
  public static final BooleanProperty OPEN_FAILURE_LINE = new BooleanProperty("openFailureLine", false);
  public static final BooleanProperty TRACK_CODE_COVERAGE = new BooleanProperty("trackCodeCoverage", false);

  private Project myProject;
  private ConsoleView myConsole;

  protected final HashMap<AbstractProperty, ArrayList<TestFrameworkPropertyListener>> myListeners = new HashMap<AbstractProperty, ArrayList<TestFrameworkPropertyListener>>();

  public TestConsoleProperties(final Storage storage, Project project) {
    super(storage);
    myProject = project;
  }

  public Project getProject() { return myProject; }

  public <T> void addListener(final AbstractProperty<T> property, final TestFrameworkPropertyListener<T> listener) {
    ArrayList<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners == null) {
      listeners = new ArrayList<TestFrameworkPropertyListener>();
      myListeners.put(property, listeners);
    }
    listeners.add(listener);
  }

  public <T> void addListenerAndSendValue(final AbstractProperty<T> property, final TestFrameworkPropertyListener<T> listener) {
    addListener(property, listener);
    listener.onChanged(property.get(this));
  }

  public <T> void removeListener(final AbstractProperty<T> property, final TestFrameworkPropertyListener listener) {
    myListeners.get(property).remove(listener);
  }

  public abstract boolean isDebug();

  public abstract boolean isPaused();

  protected <T> void onPropertyChanged(final AbstractProperty<T> property, final T value) {
    final ArrayList<TestFrameworkPropertyListener> listeners = myListeners.get(property);
    if (listeners == null) return;
    final Object[] propertyListeners = listeners.toArray();
    for (Object propertyListener : propertyListeners) {
      final TestFrameworkPropertyListener<T> listener = (TestFrameworkPropertyListener<T>)propertyListener;
      listener.onChanged(value);
    }
  }

  public void setConsole(final ConsoleView console) {
    myConsole = console;
  }


  public void dispose() {
    myListeners.clear();
  }

  public abstract RuntimeConfiguration getConfiguration();

  protected ExecutionConsole getConsole() {
    return myConsole;
  }
}