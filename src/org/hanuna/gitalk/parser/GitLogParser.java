package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;
import org.hanuna.gitalk.commitmodel.builder.CommitListBuilder;
import org.hanuna.gitalk.common.ReadOnlyList;
import org.hanuna.gitalk.common.SimpleReadOnlyList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author erokhins
 */
public class GitLogParser {
    private static final String SEPARATOR = "\\|\\-";
    private static final String regExp =
            String.format("([a-f0-9]+)%1$s((?:[a-f0-9]+)?(?:\\s[a-f0-9]+)*)%1$s(.*?)%1$s([0-9]*)%1$s(.*)", SEPARATOR);
    private static final Pattern pattern = Pattern.compile(regExp);

    public static CommitData parseCommitData(String inputStr) {
        Matcher matcher = pattern.matcher(inputStr);
        if (matcher.matches()) {
            Hash hash = Hash.buildHash(matcher.group(1));
            String parents = matcher.group(2);
            String author = matcher.group(3);
            long timeStamp = 0;
            if (matcher.group(4).length() != 0) {
                timeStamp = Long.parseLong(matcher.group(4));
            }
            String message = matcher.group(5);

            String[] parentsStr = parents.split("\\s");
            List<Hash> hashs = new ArrayList<Hash>(parentsStr.length);
            for (int i = 0; i < parentsStr.length; i++) {
                if (parentsStr[i].length() > 0) {
                    hashs.add(Hash.buildHash(parentsStr[i]));
                }
            }
            return new CommitData(hash, new SimpleReadOnlyList<Hash>(hashs), author, timeStamp, message);
        } else {
            throw new IllegalArgumentException("unexpected format of string:" + inputStr);
        }
    }

    public static ReadOnlyList<Commit> parseCommitLog(Reader inputReader) throws IOException {
        BufferedReader input;
        if (inputReader instanceof BufferedReader) {
            input = (BufferedReader) inputReader;
        } else  {
            input = new BufferedReader(inputReader);
        }
        CommitListBuilder builder = new CommitListBuilder();
        String line;
        while ((line = input.readLine()) != null) {
            CommitData data = parseCommitData(line);
            builder.append(data);
        }
        return builder.build();
    }
}
