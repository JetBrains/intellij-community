package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public class Ref {
    private final Hash commitHash;
    private final String name;
    private final Type type;

    public Ref(@NotNull Hash commitHash, @NotNull String name, @NotNull Type type) {
        this.commitHash = commitHash;
        this.name = name;
        this.type = type;

    }

    @NotNull
    public Type getType() {
        return type;
    }

    @NotNull
    public Hash getCommitHash() {
        return commitHash;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public String getShortName() {
        int ind = name.lastIndexOf("/");
        return name.substring(ind + 1);
    }


    public static enum Type {
        LOCAL_BRANCH,
        REMOTE_BRANCH,
        TAG,
        STASH
    }
}
