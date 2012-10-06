package org.hanuna.gitalk.gittree;

import org.junit.Test;

import static org.hanuna.gitalk.testUtils.*;
import static junit.framework.Assert.assertEquals;

/**
 * @author erokhins
 */
public class CommitDataTest {

    public void runTest(String input, String output) {
        CommitData cd = new CommitData(input);

        assertEquals(output, toStr(cd));

    }



    @Test
    public void simpleTest() {
        runTest("ad|-23|-smv|-34|-message", "ad|23|null|smv|34|message");

        runTest("a|-|-author|-2|-", "a|null|null|author|2|");
    }
}
