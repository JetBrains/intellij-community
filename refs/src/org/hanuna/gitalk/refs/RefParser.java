package org.hanuna.gitalk.refs;

import org.hanuna.gitalk.log.commit.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author erokhins
 */
public class RefParser {

    public static List<Ref> allRefs(@NotNull Reader reader) throws IOException {
        BufferedReader input;
        if (reader instanceof BufferedReader) {
            input = (BufferedReader) reader;
        } else  {
            input = new BufferedReader(reader);
        }
        List<Ref> refs = new ArrayList<Ref>();

        String line;
        while ((line = input.readLine()) != null) {
            Ref ref = parse(line);
            refs.add(ref);
        }
        return refs;
    }

    @Nullable
    private static String refName(@NotNull String longName, @NotNull String startStr) {
        if (longName.startsWith(startStr)) {
            return longName.substring(startStr.length());
        } else {
            return null;
        }
    }

    @NotNull
    public static Ref parse(@NotNull String input) {
        String[] split = input.split(" ");
        if (split.length != 2) {
            throw new IllegalArgumentException("input have " + split.length + " parts");
        }
        Hash hash = Hash.build(split[0]);
        String longPathRef = split[1];
        if (longPathRef.equals("HEAD")) {
            return new Ref(hash, "HEAD", Ref.Type.LOCAL_BRANCH);
        }

        if (longPathRef.equals("refs/stash")) {
            return new Ref(hash, "stash", Ref.Type.STASH);
        }

        String name;
        if ((name = refName(longPathRef, "refs/heads/")) != null) {
            return new Ref(hash, name, Ref.Type.LOCAL_BRANCH);
        }
        if ((name = refName(longPathRef, "refs/remotes/")) != null) {
            return new Ref(hash, name, Ref.Type.REMOTE_BRANCH);
        }
        if ((name = refName(longPathRef, "refs/tags/")) != null) {
            return new Ref(hash, name, Ref.Type.TAG);
        }

        throw new IllegalArgumentException("Illegal path ref: " + longPathRef);
    }
}
