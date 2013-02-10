package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.log.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class RefsModel {
    private final List<Ref> allRefs;
    private final Set<Hash> trackedCommitHashes = new HashSet<Hash>();

    public RefsModel(List<Ref> allRefs) {
        this.allRefs = allRefs;
        computeTrackedCommitHash();
    }

    private void computeTrackedCommitHash() {
        for (Ref ref : allRefs) {
            trackedCommitHashes.add(ref.getCommitHash());
        }
    }

    @NotNull
    public List<Ref> refsToCommit(@NotNull Hash hash) {
        List<Ref> refs = new ArrayList<Ref>();
        if (trackedCommitHashes.contains(hash)) {
            for (Ref ref : allRefs) {
                if (ref.getCommitHash().equals(hash)) {
                    refs.add(ref);
                }
            }
        }
        return refs;
    }

    @NotNull
    public Set<Hash> getTrackedCommitHashes() {
        return Collections.unmodifiableSet(trackedCommitHashes);
    }

    @NotNull
    public List<Ref> getAllRefs() {
        return Collections.unmodifiableList(allRefs);
    }


}
