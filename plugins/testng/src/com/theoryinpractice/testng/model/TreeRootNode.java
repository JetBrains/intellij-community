package com.theoryinpractice.testng.model;

/**
 * @author Hani Suleiman Date: Jul 31, 2005 Time: 10:15:02 PM
 */
public class TreeRootNode extends TestProxy
{
    private boolean inProgress;
    
    public TreeRootNode() {
    }

    @Override
    public boolean isInProgress() {
        return inProgress;
    }

    public void setInProgress(boolean inProgress) {
        this.inProgress = inProgress;
    }
}
