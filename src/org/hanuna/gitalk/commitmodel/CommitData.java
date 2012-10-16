package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import org.hanuna.gitalk.common.ReadOnlyList;

/**
 * @author erokhins
 */
public class CommitData {
    @NotNull
    private final Hash hash;
    @NotNull
    private final ReadOnlyList<Hash> parents;
    @NotNull
    private final String author;
    private final long timeStamp;
    @NotNull
    private final String commitMessage;

    public CommitData(Hash hash, ReadOnlyList<Hash> parents, String author, long timeStamp, String commitMessage) {
        this.hash = hash;
        this.parents = parents;
        this.author = author;
        this.timeStamp = timeStamp;
        this.commitMessage = commitMessage;
    }

    public Hash getHash() {
        return hash;
    }

    public ReadOnlyList<Hash> getParentsHash() {
        return parents;
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
