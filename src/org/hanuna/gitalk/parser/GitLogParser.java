package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.commitmodel.Hash;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hanuna.gitalk.common.MyAssert.myAssert;

/**
 * @author erokhins
 */
public class GitLogParser {
    private static final String SEPARATOR = "\\|\\-";
    private static final String regExp =
            String.format("([a-f0-9]+)%1$s([a-f0-9]+)?\\s?((?<=[a-f0-9]\\s)[a-f0-9]+)?%1$s(.*?)%1$s([0-9]+)%1$s(.*)", SEPARATOR);
    private static final Pattern pattern = Pattern.compile(regExp);

    public static CommitData parseCommitData(String inputStr) {
        Matcher matcher = pattern.matcher(inputStr);
        if (matcher.matches()) {
            Hash hash = Hash.buildHash(matcher.group(1));
            String mainParent = matcher.group(2);
            String secondParent = matcher.group(3);
            String author = matcher.group(4);
            long timeStamp = Long.parseLong(matcher.group(5));
            String message = matcher.group(6);

            myAssert(mainParent != null || secondParent == null, "secondParent != null, but mainParent is null");
            Hash mainParentHash = mainParent == null ? null : Hash.buildHash(mainParent);
            Hash secondParentHash = secondParent == null ? null : Hash.buildHash(secondParent);
            return new CommitData(hash, mainParentHash, secondParentHash, author, timeStamp, message);
        } else {
            throw new IllegalArgumentException("unexpected format of string");
        }
    }
}
