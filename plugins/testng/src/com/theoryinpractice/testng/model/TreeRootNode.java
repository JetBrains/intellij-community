package com.theoryinpractice.testng.model;

/**
 * @author Hani Suleiman Date: Jul 31, 2005 Time: 10:15:02 PM
 */
public class TreeRootNode extends TestProxy
{
    private boolean inProgress;
    private boolean isStarted;

    public TreeRootNode() {
        inProgress = false;
        isStarted = false;
    }

    @Override
    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }

    public boolean isStarted() {
        return isStarted;
    }

    public void setStarted(boolean started) {
        isStarted = started;
    }
}
