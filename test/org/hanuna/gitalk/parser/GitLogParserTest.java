package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.commitmodel.CommitData;
import org.hanuna.gitalk.common.readonly.ReadOnlyList;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.TestUtils.toShortStr;
import static org.hanuna.gitalk.TestUtils.toStr;

/**
 * @author erokhins
 */
public class GitLogParserTest {
    private void runTestParseCommitData(String in, String out) {
        CommitData cd = GitLogParser.parseCommitData(in);
        assertEquals(out, toStr(cd));
    }

    @Test
    public void testParseCommitData() throws Exception {
        runTestParseCommitData("a12f|-|-author s|-132352112|- message", "a12f|-author s|-132352112|- message");
        runTestParseCommitData("a|-b c|-|-13|-message", "a|-b|-c|-|-13|-message");
        runTestParseCommitData("adf23|-adf2|-a|-1|-mes|-age", "adf23|-adf2|-a|-1|-mes|-age");
        runTestParseCommitData("adf23|-a1 a2 a3|-a|-|-mes|-age", "adf23|-a1|-a2|-a3|-a|-0|-mes|-age");
    }


    private void runLogParserTest(String in, String out) throws IOException {
        String input = in.replace("\n", "|-aut|-132352112|-mes\n") + "|-aut|-132352112|-mes";
        String output = out.replace(" ", "|-") + "\n";
        ReadOnlyList<Commit> commits = GitLogParser.parseCommitLog(new StringReader(input));
        assertEquals(output, toShortStr(commits));
    }

    @Test
    public void testLogParser() throws IOException {
        runLogParserTest("12|-", "0|-12");

        runLogParserTest(
                "12|-af\n" +
                "af|-",

                "0|-1|-12\n" +
                "1|-af"
        );

        runLogParserTest(
                "a0|-a3 a1\n" +
                "a1|-a2 a4\n" +
                "a2|-a3 a5 a8\n" +
                "a3|-a6\n" +
                "a4|-a7\n" +
                "a5|-a7\n" +
                "a6|-a7\n" +
                "a7|-\n" +
                "a8|-",

                "0|-3 1|-a0\n" +
                "1|-2 4|-a1\n" +
                "2|-3 5 8|-a2\n" +
                "3|-6|-a3\n" +
                "4|-7|-a4\n" +
                "5|-7|-a5\n" +
                "6|-7|-a6\n" +
                "7|-a7\n" +
                "8|-a8"
        );
    }

}
