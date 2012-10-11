package org.hanuna.gitalk.parser;

import org.hanuna.gitalk.commitmodel.CommitData;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.TestUtils.toStr;

/**
 * @author erokhins
 */
public class GitLogParserTest {
    private void runTest(String input, String output) {
        CommitData cd = GitLogParser.parseCommitData(input);
        assertEquals(output, toStr(cd));
    }

    @Test
    public void testParseCommitData() throws Exception {
        runTest("a12f|-|-author s|-132352112|- message", "a12f|-null|-null|-author s|-132352112|- message");
        runTest("a|-b c|-|-13|-message", "a|-b|-c|-|-13|-message");
        runTest("adf23|-adf2|-a|-1|-mes|-age", "adf23|-adf2|-null|-a|-1|-mes|-age");
    }
}
