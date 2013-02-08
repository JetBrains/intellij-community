package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.log.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class RefsModel {
    public static RefsModel existedCommitRefs(List<Ref> allRefs) {

        return new RefsModel(allRefs);
    }

    private final List<Ref> allRefs;
    private final Set<Hash> trackedHash = new HashSet<Hash>();

    public RefsModel(List<Ref> allRefs) {
        this.allRefs = allRefs;
        for (Ref ref : allRefs) {
            trackedHash.add(ref.getCommitHash());
        }
    }

    // modifiable List
    @NotNull
    public List<Ref> refsToCommit(@NotNull Hash hash) {
        List<Ref> refs = new ArrayList<Ref>();
        if (trackedHash.contains(hash)) {
            for (Ref ref : allRefs) {
                if (ref.getCommitHash().equals(hash)) {
                    refs.add(ref);
                }
            }
        }
        return refs;
    }

    @NotNull
    public Set<Hash> getOrderedLogTrackedCommit() {
        return Collections.unmodifiableSet(trackedHash);
    }
}
