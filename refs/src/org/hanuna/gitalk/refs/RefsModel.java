package org.hanuna.gitalk.refs;

<<<<<<< HEAD
import org.hanuna.gitalk.commitmodel.Commit;
=======
>>>>>>> 39024864242c12535f1ade0c97ee53f034b98a38
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author erokhins
 */
public class RefsModel {
<<<<<<< HEAD
    public static RefsModel existedCommitRefs(List<Ref> allRefs, List<Commit> commits) {
        Set<Hash> refCommits = new HashSet<Hash>();
        for (Ref ref : allRefs) {
            refCommits.add(ref.getCommitHash());
        }
        Set<Hash> existedCommitsRefs = new HashSet<Hash>();
        for (Commit commit : commits) {
            if (refCommits.contains(commit.hash())) {
                existedCommitsRefs.add(commit.hash());
            }
        }

        List<Ref> existedRef = new ArrayList<Ref>();
        for (Ref ref : allRefs) {
            if (existedCommitsRefs.contains(ref.getCommitHash())) {
                existedRef.add(ref);
            }
        }
        return new RefsModel(existedRef);
    }

=======
>>>>>>> 39024864242c12535f1ade0c97ee53f034b98a38
    private final List<Ref> allRefs;
    private final Set<Hash> trackedHash = new HashSet<Hash>();
    private final List<Ref> localBranches = new ArrayList<Ref>();
    private final List<Ref> remoteBranches = new ArrayList<Ref>();

    public RefsModel(List<Ref> allRefs) {
        this.allRefs = allRefs;
        for (Ref ref : allRefs) {
            trackedHash.add(ref.getCommitHash());
            if (ref.getType() == Ref.Type.LOCAL_BRANCH) {
                localBranches.add(ref);
            }
            if (ref.getType() == Ref.Type.REMOTE_BRANCH) {
                remoteBranches.add(ref);
            }
        }
    }

    @Nullable
<<<<<<< HEAD
    public Ref refToCommit(@NotNull Hash hash) {
=======
    public Ref refInToCommit(@NotNull Hash hash) {
>>>>>>> 39024864242c12535f1ade0c97ee53f034b98a38
        if (trackedHash.contains(hash)) {
            for (Ref ref : allRefs) {
                if (ref.getCommitHash().equals(hash)) {
                    return ref;
                }
            }
        }
        return null;
    }

    public ReadOnlyList<Ref> localBranches() {
        return ReadOnlyList.newReadOnlyList(localBranches);
    }

    public ReadOnlyList<Ref> remoteBranches() {
        return ReadOnlyList.newReadOnlyList(remoteBranches);
    }

}
