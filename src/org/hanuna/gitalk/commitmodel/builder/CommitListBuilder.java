package org.hanuna.gitalk.commitmodel.builder;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.hanuna.gitalk.common.readonly.SimpleReadOnlyList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author erokhins
 */
public class CommitListBuilder {
    private final List<Commit> commits = new ArrayList<Commit>();
    private final Map<Hash, MutableCommit> cache = new HashMap<Hash, MutableCommit>();
    private boolean wasBuild = false;

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
        assert ! wasBuild : "builder was run, but append request";
        MutableCommit commit = getCommit(logData.getHash());
        List<Commit> parents = new ArrayList<Commit>(logData.getParentsHash().size());
        for (Hash hash : logData.getParentsHash()) {
            MutableCommit parent = getCommit(hash);
            parents.add(parent);
        }
        removeCommit(logData.getHash());
        CommitData commitData = new CommitData(commits.size(), new SimpleReadOnlyList<Commit>(parents),
                logData.getCommitMessage(), logData.getAuthor(), logData.getTimeStamp());
        commit.setCommitData(commitData);
        commits.add(commit);
    }

    private void checkEmptyCache() {
        if (cache.size() > 0) {
            throw new IllegalStateException("several commits not found in log");
        }
    }

    @NotNull
    public ReadOnlyList<Commit> build(boolean allLogReadied) {
        wasBuild = true;
        if (allLogReadied) {
            checkEmptyCache();
        }
        return new SimpleReadOnlyList<Commit>(commits);
    }

}
