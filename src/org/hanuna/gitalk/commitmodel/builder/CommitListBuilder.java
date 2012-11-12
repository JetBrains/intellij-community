package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitLogData;
import org.hanuna.gitalk.commitmodel.CommitsModel;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.readonly.SimpleReadOnlyList;
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

    public void append(@NotNull CommitLogData logData) {
        MutableCommit commit = getCommit(logData.getHash());
        List<Commit> parents = new ArrayList<Commit>(logData.getParentsHash().size());
        for (Hash hash : logData.getParentsHash()) {
            MutableCommit parent = getCommit(hash);
            parents.add(parent);
            parent.addChildren(commit);
        }
        removeCommit(logData.getHash());
        commit.set(logData, new SimpleReadOnlyList<Commit>(parents), commits.size());
        commits.add(commit);
    }

    private void checkEmptyCache() {
        if (cache.size() > 0) {
            throw new IllegalStateException();
        }
    }

    @NotNull
    public CommitsModel build(boolean fullLog) {
        if (fullLog) {
            checkEmptyCache();
        }
        ReadOnlyList<Commit> commits1 = new SimpleReadOnlyList<Commit>(commits);
        return CommitsModel.buildModel(commits1, fullLog);
    }

}
