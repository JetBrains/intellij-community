package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.log.commit.Commit;
import org.hanuna.gitalk.log.commit.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class RefsModel {
    public static RefsModel existedCommitRefs(List<Ref> allRefs, List<Commit> commits) {
        Set<Hash> refCommits = new HashSet<Hash>();
        for (Ref ref : allRefs) {
            refCommits.add(ref.getCommitHash());
        }
        Set<Hash> existedCommitsRefs = new HashSet<Hash>();
        List<Commit> orderedLogExistedCommits = new ArrayList<Commit>();
        for (Commit commit : commits) {
            if (refCommits.contains(commit.getCommitHash())) {
                existedCommitsRefs.add(commit.getCommitHash());
                orderedLogExistedCommits.add(commit);
            }
        }

        List<Ref> existedRef = new ArrayList<Ref>();
        for (Ref ref : allRefs) {
            if (existedCommitsRefs.contains(ref.getCommitHash())) {
                existedRef.add(ref);
            }
        }
        return new RefsModel(existedRef, orderedLogExistedCommits);
    }

    private final List<Ref> allRefs;
    private final Set<Hash> trackedHash = new HashSet<Hash>();
    private final List<Commit> orderedLogTrackedCommit;

    public RefsModel(List<Ref> allRefs, List<Commit> orderedLogTrackedCommit) {
        this.allRefs = allRefs;
        this.orderedLogTrackedCommit = orderedLogTrackedCommit;
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
    public List<Commit> getOrderedLogTrackedCommit() {
        return Collections.unmodifiableList(orderedLogTrackedCommit);
    }
}
