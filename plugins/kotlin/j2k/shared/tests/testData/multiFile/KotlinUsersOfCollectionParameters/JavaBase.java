package test;

import java.util.*;

public class JavaBase {
    public void handle(List<String> items) {
        System.out.println(items.size());
    }

    public void fill(List<List<String>> lists) {
        lists.add(new ArrayList<>());
    }
}
