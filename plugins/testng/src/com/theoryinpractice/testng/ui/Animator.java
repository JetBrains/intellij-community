package com.theoryinpractice.testng.ui;

import com.intellij.util.Alarm;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.theoryinpractice.testng.model.TestProxy;
import com.theoryinpractice.testng.model.TestTreeBuilder;

import javax.swing.*;

public class Animator implements Runnable
{
    private static final Icon icons[];
    private static final int REPAINT_INTERVAL = 100;
    private TestTreeBuilder treeBuilder;
    private TestProxy proxy;
    private Alarm alarm;
    public static final Icon PAUSED_ICON = TestsUIUtil.loadIcon("testPaused");

    static {
        icons = new Icon[8];
        for(int i = 0; i < 8; i++)
            icons[i] = TestsUIUtil.loadIcon("testInProgress" + (i + 1));

    }

    public Animator(TestTreeBuilder builder) {
        this.treeBuilder = builder;
        alarm = new Alarm();
    }

    public TestProxy getCurrentTestCase() {
      return proxy;
    }

    public void setCurrentTestCase(TestProxy testproxy) {
        proxy = testproxy;
        scheduleRepaint();
    }

    public void run() {
        if(proxy != null) {
            repaint();
        }
        scheduleRepaint();
    }

    private void repaint() {
        treeBuilder.repaintWithParents(proxy);
    }

    private void scheduleRepaint() {
        if(alarm == null)
            return;
        alarm.cancelAllRequests();
        if(proxy != null) {
            alarm.addRequest(this, REPAINT_INTERVAL);
        }
    }

    public static Icon getCurrentFrame() {
        int i = (int)((System.currentTimeMillis() % 800) / 100);
        return icons[i];
    }

    private void cancelAlarm() {
        if(alarm != null)
            alarm.cancelAllRequests();
        alarm = null;
    }

    public void stop() {
        if(proxy != null)
            repaint();
        setCurrentTestCase(null);
        cancelAlarm();
    }

    public void dispose() {
        treeBuilder = null;
        proxy = null;
        alarm = null;
    }
}
