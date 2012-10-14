package org.hanuna.gitalk.commitmodel;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.*;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<CommitNode> commits = new ArrayList<CommitNode>();
    private final MutableCommitNodeCache cache = new MutableCommitNodeCache();

    public void append(@NotNull CommitData data) {
        cache.isEmpty();
        cache.get(null);
        MutableCommitNode commit = cache.pop(data.getHash());
        CommitNode mainParent = cache.get(data.getMainParentHash());
        CommitNode secondParent = cache.get(data.getSecondParentHash());
        commit.set(data, mainParent, secondParent, commits.size());
        commits.add(commit);
    }

    public CommitList build() {
        if (!cache.isEmpty()){
            throw new NotFinalise();
        }
        return new SimpleCommitList(commits);
    }

    private static class MutableCommitNodeCache {
        private final Map<Hash, MutableCommitNode> cache = new HashMap<Hash, MutableCommitNode>();


        @NotNull
        public MutableCommitNode get(@Nullable Hash hash) {
            if (hash == null) {
                return null;
            }
            MutableCommitNode commit = cache.get(hash);
            if (commit == null) {
                commit = new MutableCommitNode(hash);
                cache.put(hash, commit);
            }
            return commit;
        }

        @NotNull
        public MutableCommitNode pop(@Nullable Hash hash) {
            if (hash == null) {
                return null;
            }
            MutableCommitNode commit = cache.get(hash);
            if (commit == null) {
                commit = new MutableCommitNode(hash);
            } else {
                cache.remove(hash);
            }
            return commit;
        }

        public boolean isEmpty() {
            return cache.isEmpty();
        }
    }

}
