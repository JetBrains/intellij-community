/*
 * Created on Apr 25, 2005
 *
 * @author Fabio Zadrozny
 */
package com.jetbrains.python.console.pydev;

/**
 * @author Fabio Zadrozny
 */
public interface IPyCompletionProposal {

    int PRIORITY_LOCALS = -1;

    //those have local priorities, but for some reason have a lower priority than locals
    int PRIORITY_LOCALS_1 = 0;
    int PRIORITY_LOCALS_2 = 1;

    int PRIORITY_DEFAULT = 10;
    int PRIORITY_GLOBALS = 50;
    int PRIORITY_PACKAGES = 100;

    /**
     * @return the priority for showing this completion proposal, so that lower priorities are
     * shown earlier in the list.
     */
    public int getPriority();
}