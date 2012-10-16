package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.*;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<Commit> commits = new ArrayList<Commit>();
    private final MutableCommitNodeCache cache = new MutableCommitNodeCache();

    public void append(@NotNull CommitData data) {
        MutableCommit commit = cache.pop(data.getHash());
        Commit mainParent = cache.get(data.getMainParentHash());
        Commit secondParent = cache.get(data.getSecondParentHash());
        commit.set(data, mainParent, secondParent, commits.size());
        commits.add(commit);
    }

    public CommitList build() {
        cache.checkEmpty();
        return new SimpleCommitList(commits);
    }

    private static class MutableCommitNodeCache {
        private final Map<Hash, MutableCommit> cache = new HashMap<Hash, MutableCommit>();


        @NotNull
        public MutableCommit get(@Nullable Hash hash) {
            if (hash == null) {
                return null;
            }
            MutableCommit commit = cache.get(hash);
            if (commit == null) {
                commit = new MutableCommit(hash);
                cache.put(hash, commit);
            }
            return commit;
        }

        @NotNull
        public MutableCommit pop(@Nullable Hash hash) {
            if (hash == null) {
                return null;
            }
            MutableCommit commit = cache.get(hash);
            if (commit == null) {
                commit = new MutableCommit(hash);
            }
                cache.remove(hash);
            return commit;
        }

        public void checkEmpty() {
            if (!cache.isEmpty()) {
                throw new NotFinalise(cache.keySet());
            }
        }
    }

}
