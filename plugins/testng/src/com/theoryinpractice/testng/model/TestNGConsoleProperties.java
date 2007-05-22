package com.theoryinpractice.testng.model;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.execution.util.StoringPropertyContainer;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import com.intellij.util.config.Storage;
import com.theoryinpractice.testng.TestNGConfiguration;
import com.theoryinpractice.testng.TestNGConsoleView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

public class TestNGConsoleProperties extends StoringPropertyContainer
{
    public static final BooleanProperty SCROLL_TO_STACK_TRACE = new BooleanProperty("scrollToStackTrace", false);
    public static final BooleanProperty SELECT_FIRST_DEFECT = new BooleanProperty("selectFirtsDefect", false);
    public static final BooleanProperty TRACK_RUNNING_TEST = new BooleanProperty("trackRunningTest", true);
    public static final BooleanProperty HIDE_PASSED_TESTS = new BooleanProperty("hidePassedTests", true);
    public static final BooleanProperty SCROLL_TO_SOURCE = new BooleanProperty("scrollToSource", false);
    public static final BooleanProperty OPEN_FAILURE_LINE = new BooleanProperty("openFailureLine", false);
    private TestNGConsoleView console;
    protected final HashMap<AbstractProperty,ArrayList<TestNGPropertyListener>> myListeners;
    private static final String PREFIX = "TestNGSupport.";
    private final TestNGConfiguration config;

    public TestNGConsoleProperties(TestNGConfiguration config)
    {
        this(config, new Storage.PropertiesComponentStorage(PREFIX, PropertiesComponent.getInstance()));
    }

    public TestNGConsoleProperties(TestNGConfiguration config, Storage storage)
    {
        super(storage);
        myListeners = new HashMap<AbstractProperty, ArrayList<TestNGPropertyListener>>();
        this.config = config;
    }

    public Project getProject()
    {
        return config.getProject();
    }

    public TestNGConfiguration getConfiguration()
    {
        return config;
    }

    public void setConsole(TestNGConsoleView console)
    {
        this.console = console;
    }

    public void addListener(AbstractProperty property, TestNGPropertyListener listener)
    {
        ArrayList<TestNGPropertyListener> arraylist = myListeners.get(property);
        if(arraylist == null)
        {
            arraylist = new ArrayList<TestNGPropertyListener>();
            myListeners.put(property, arraylist);
        }
        arraylist.add(listener);
    }

    public void addListenerAndSendValue(AbstractProperty abstractproperty, TestNGPropertyListener listener)
    {
        addListener(abstractproperty, listener);
        listener.onChanged(abstractproperty.get(this));
    }

    public void removeListener(AbstractProperty abstractproperty, TestNGPropertyListener listener)
    {
        myListeners.get(abstractproperty).remove(listener);
    }

    public DebuggerSession getDebuggerSession()
    {
        DebuggerManagerEx debuggermanagerex = DebuggerManagerEx.getInstanceEx(getProject());
        if(debuggermanagerex == null)
            return null;
        Collection<DebuggerSession> collection = debuggermanagerex.getSessions();
        for(DebuggerSession session : collection)
        {
            if(console == session.getProcess().getExecutionResult().getExecutionConsole())
                return session;
        }

        return null;
    }

    public void dispose()
    {
        myListeners.clear();
    }
    
    @Override
    protected void onPropertyChanged(AbstractProperty property, Object obj)
    {
        ArrayList<TestNGPropertyListener> list = myListeners.get(property);
        if(list == null)
            return;
        Object array[] = list.toArray();
        for(Object aArray : array) {
            TestNGPropertyListener listener = (TestNGPropertyListener)aArray;
            listener.onChanged(obj);
        }

    }
}
