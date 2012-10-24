package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.CommitsModel;
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

    @NotNull
    private MutableCommit getCommit(@NotNull Hash hash) {
        MutableCommit commit = cache.get(hash);
        if (commit == null) {
            commit = new MutableCommit(hash);
            cache.put(hash, commit);
        }
        return commit;
    }

    private void removeCommit(@NotNull Hash hash) {
        cache.remove(hash);
    }

    public void append(@NotNull CommitData data) {
        MutableCommit commit = getCommit(data.getHash());
        List<Commit> parents = new ArrayList<Commit>(data.getParentsHash().size());
        for (Hash hash : data.getParentsHash()) {
            MutableCommit parent = getCommit(hash);
            parents.add(parent);
            parent.addChildren(commit);
        }
        removeCommit(data.getHash());
        commit.set(data, new SimpleReadOnlyList<Commit>(parents), commits.size());
        commits.add(commit);
    }

    @NotNull
    public CommitsModel build() {
        ReadOnlyList<Commit> commits1 = new SimpleReadOnlyList<Commit>(commits);
        return CommitsModel.buildModel(commits1);
    }

}
