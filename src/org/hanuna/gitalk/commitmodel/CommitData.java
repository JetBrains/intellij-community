package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

/**
 * @author erokhins
 */
public class CommitData {
    @NotNull
    private final Hash hash;
    @Nullable
    private final Hash mainParent;
    @Nullable
    private final Hash secondParent;
    private final String author;
    private final long timeStamp;
    private final String commitMessage;

    public CommitData(Hash hash, Hash mainParent, Hash secondParent, String author, long timeStamp, String commitMessage) {
        this.hash = hash;
        this.mainParent = mainParent;
        this.secondParent = secondParent;
        this.author = author;
        this.timeStamp = timeStamp;
        this.commitMessage = commitMessage;
    }

    public Hash getHash() {
        return hash;
    }

    public Hash getMainParentHash() {
        return mainParent;
    }

    public Hash getSecondParentHash() {
        return secondParent;
    }

    public String getAuthor() {
        return author;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public String getCommitMessage() {
        return commitMessage;
    }
}
