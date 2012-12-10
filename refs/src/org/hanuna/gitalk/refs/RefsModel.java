package org.hanuna.gitalk.refs;

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
    public Ref refInToCommit(@NotNull Hash hash) {
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
