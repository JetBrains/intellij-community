package org.hanuna.gitalk.gittree;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

import static junit.framework.Assert.assertEquals;
import static org.hanuna.gitalk.testUtils.toShortStr;

/**
 * @author erokhins
 */
public class CommitsTreeTest {

    /**
     *
     * @param input example: "1|2 3\n2|3\n1|3\n";
     * @param output
     */
    public void runTreeTest(String input, String output) throws IOException {
        input = input.replace("|", "|-");
        input = input.replace("\n", "|-smv|-34|-message\n");
        StringReader r = new StringReader(input);
        BufferedReader br = new BufferedReader(r);
        CommitsTree ct = new CommitsTree(br);
        assertEquals(output, toShortStr(ct));
    }

    @Test
    public void simpleTest() throws IOException {
        runTreeTest("a|b c\nb|d\nc|\nd|\n", "0|1|2|a\n1|3|b\n2|c\n3|d\n");

        runTreeTest("a|b\nb|c\nc|\n", "0|1|a\n1|2|b\n2|c\n");
    }

}
