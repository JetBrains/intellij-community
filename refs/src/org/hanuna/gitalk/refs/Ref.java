package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public final class Ref {
    private final Hash commitHash;
    private final String name;
    private final RefType type;

    public Ref(@NotNull Hash commitHash, @NotNull String name, @NotNull RefType type) {
        this.commitHash = commitHash;
        this.name = name;
        this.type = type;

    }

    @NotNull
    public RefType getType() {
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

    @Override
    public String toString() {
        return "Ref{" +
                "commitHash=" + commitHash +
                ", name='" + name + '\'' +
                ", type=" + type +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Ref ref = (Ref) o;

        if (commitHash != null ? !commitHash.equals(ref.commitHash) : ref.commitHash != null) return false;
        if (name != null ? !name.equals(ref.name) : ref.name != null) return false;
        if (type != ref.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = commitHash != null ? commitHash.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        return result;
    }

    public static enum RefType {
        LOCAL_BRANCH,
        REMOTE_BRANCH,
        TAG,
        STASH,
        ANOTHER
    }
}
