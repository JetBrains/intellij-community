package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RefParser {

    // e25b7d8f (HEAD, refs/remotes/origin/master, refs/remotes/origin/HEAD, refs/heads/master)
    public static List<Ref> parseCommitRefs(@NotNull String input) {
        int firstSpaceIndex = input.indexOf(' ');
        String strHash = input.substring(0, firstSpaceIndex);
        Hash hash = Hash.build(strHash);
        String refPaths = input.substring(firstSpaceIndex + 2, input.length() - 1);
        String[] longRefPaths = refPaths.split(", ");
        List<Ref> refs = new ArrayList<Ref>();
        for (String longRefPatch : longRefPaths) {
            refs.add(createRef(hash, longRefPatch));
        }
        return refs;
    }

    @Nullable
    private static String getRefName(@NotNull String longRefPath, @NotNull String startPatch) {
        if (longRefPath.startsWith(startPatch)) {
            return longRefPath.substring(startPatch.length());
        } else {
            return null;
        }
    }

    // example input: fb29c80 refs/tags/92.29
    @NotNull
    private static Ref createRef(@NotNull Hash hash, @NotNull String longRefPath) {
        if (longRefPath.equals("HEAD")) {
            return new Ref(hash, "HEAD", Ref.Type.LOCAL_BRANCH);
        }

        if (longRefPath.equals("refs/stash")) {
            return new Ref(hash, "stash", Ref.Type.STASH);
        }

        String name;
        if ((name = getRefName(longRefPath, "refs/heads/")) != null) {
            return new Ref(hash, name, Ref.Type.LOCAL_BRANCH);
        }
        if ((name = getRefName(longRefPath, "refs/remotes/")) != null) {
            return new Ref(hash, name, Ref.Type.REMOTE_BRANCH);
        }
        if ((name = getRefName(longRefPath, "refs/tags/")) != null) {
            return new Ref(hash, name, Ref.Type.TAG);
        }

        throw new IllegalArgumentException("Illegal path ref: " + longRefPath);
    }
}
