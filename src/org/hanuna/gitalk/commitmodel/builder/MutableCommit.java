package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
class MutableCommit implements Commit {
    private final Hash hash;
    private ReadOnlyList<Commit> parents;
    private boolean hasChildren;
    private String author;
    private String message;
    private long timeStamp;
    private int index = -1;
    private int countNewUniqueCommitsAmongParents;

    public MutableCommit(Hash hash) {
        this.hash = hash;
    }

    public void set(@NotNull CommitData data, @NotNull ReadOnlyList<Commit> parents, boolean hasChildren,
                    int countNewUniqueCommitsAmongParents, int index) {
        this.parents = parents;
        this.hasChildren = hasChildren;
        this.countNewUniqueCommitsAmongParents = countNewUniqueCommitsAmongParents;
        this.author = data.getAuthor();
        this.message = data.getCommitMessage();
        this.timeStamp = data.getTimeStamp();
        this.index = index;
    }


    @NotNull
    @Override
    public Hash hash() {
        return hash;
    }

    @NotNull
    @Override
    public ReadOnlyList<Commit> getParents() {
        return parents;
    }

    @Override
    public boolean hasChildren() {
        return hasChildren;
    }

    @Override
    public int countNewUniqueCommitsAmongParents() {
        return countNewUniqueCommitsAmongParents;
    }

    @NotNull
    @Override
    public String getMessage() {
        return message;
    }

    @NotNull
    @Override
    public String getAuthor() {
        return author;
    }

    @Override
    public long getTimeStamp() {
        return timeStamp;
    }


    public boolean equals(Object obj) {
        if (obj instanceof Commit) {
            return ((Commit) obj).hash().equals(hash);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return hash.hashCode();
    }

    @Override
    public int index() {
        assert index != -1 : "index undefined";
        return index;
    }

}
