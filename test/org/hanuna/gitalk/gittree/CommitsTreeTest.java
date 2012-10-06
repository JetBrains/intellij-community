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
        System.out.println(input);
        StringReader r = new StringReader(input);
        BufferedReader br = new BufferedReader(r);
        CommitsTree ct = new CommitsTree(br);
        assertEquals(output, toShortStr(ct));
    }

    @Test
    public void simpleTest() throws IOException {
        runTreeTest("0|1 2\n1|3\n2|\n3|\n", "0|1|2|0\n1|3|");
    }

}
