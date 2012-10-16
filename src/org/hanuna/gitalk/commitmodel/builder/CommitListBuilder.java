package org.hanuna.gitalk.commitmodel.builder;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;
import org.hanuna.gitalk.commitmodel.*;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.util.*;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<Commit> commits = new ArrayList<Commit>();
    private final MutableCommitNodeCache cache = new MutableCommitNodeCache();

    public void append(@NotNull CommitData data) {
        MutableCommit commit = cache.pop(data.getHash());
        List<Commit> parents = new ArrayList<Commit>(data.getParentsHash().size());
        for (Hash hash : data.getParentsHash()) {
            parents.add(cache.get(hash));
        }
        commit.set(data, new SimpleReadOnlyList<Commit>(parents), commits.size());
        commits.add(commit);
    }

    @NotNull
    public ReadOnlyList<Commit> build() {
        cache.checkEmpty();
        return new SimpleReadOnlyList<Commit>(commits);
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
                commit = new MutableCommit();
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
                commit = new MutableCommit();
            }
                cache.remove(hash);
            return commit;
        }

        public void checkEmpty() {
            if (!cache.isEmpty()) {
                throw new NotFullLog(cache.keySet());
            }
        }
    }

}
