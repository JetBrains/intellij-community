package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<Commit> commits = new ArrayList<Commit>();
    private final Map<Hash, MutableCommit> cache = new HashMap<Hash, MutableCommit>();
    // commits, which was read or parent of read commit
    private int countUniqueCommits = 0;

    @NotNull
    private MutableCommit getCommit(@NotNull Hash hash) {
        MutableCommit commit = cache.get(hash);
        if (commit == null) {
            countUniqueCommits++;
            commit = new MutableCommit(hash);
            cache.put(hash, commit);
        }
        return commit;
    }

    private void removeCommit(@NotNull Hash hash) {
        cache.remove(hash);
    }

    public void append(@NotNull CommitData data) {
        boolean hasChildren = true;
        int startCountCommits = this.countUniqueCommits;
        MutableCommit commit = getCommit(data.getHash());
        if (startCountCommits < this.countUniqueCommits) {
            hasChildren = false;
            startCountCommits = this.countUniqueCommits;
        }
        List<Commit> parents = new ArrayList<Commit>(data.getParentsHash().size());
        for (Hash hash : data.getParentsHash()) {
            MutableCommit parent = getCommit(hash);
            parents.add(parent);
        }
        removeCommit(data.getHash());
        commit.set(data, new SimpleReadOnlyList<Commit>(parents), hasChildren,
                    this.countUniqueCommits - startCountCommits, commits.size());
        commits.add(commit);
    }

    @NotNull
    public ReadOnlyList<Commit> build() {
        return new SimpleReadOnlyList<Commit>(commits);
    }

}
