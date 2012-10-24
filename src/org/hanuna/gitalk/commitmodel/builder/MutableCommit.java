package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
class MutableCommit implements Commit {
    private final Hash hash;
    private boolean wasRead = false;
    private ReadOnlyList<Commit> parents;
    private List<Commit> childrens = new ArrayList<Commit>(1);
    private String author;
    private String message;
    private long timeStamp;
    private int index = -1;

    public MutableCommit(Hash hash) {
        this.hash = hash;
    }

    public void addChildren(Commit children) {
        childrens.add(children);
    }

    public void set(@NotNull CommitData data, @NotNull ReadOnlyList<Commit> parents, int index) {
        assert !wasRead : "double set commit data";
        wasRead = true;
        this.parents = parents;
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

    @Override
    public boolean wasRead() {
        return wasRead;
    }

    @NotNull
    @Override
    public ReadOnlyList<Commit> getParents() {
        return parents;
    }

    @NotNull
    @Override
    public ReadOnlyList<Commit> getChildren() {
        return new SimpleReadOnlyList<Commit>(childrens);
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
        assert !wasRead : "index undefined";
        return index;
    }

}
