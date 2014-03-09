package de.plushnikov.temp;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class TestTemp {

    private int a;
    private int b;
    private String c;

    public static void main(String[] args) {
        TestTemp testTemp = new TestTemp();
        testTemp.setA(1);

        testTemp.getB();
        System.out.println(testTemp.getC());
        log.warn(testTemp.toString());
    }
}
