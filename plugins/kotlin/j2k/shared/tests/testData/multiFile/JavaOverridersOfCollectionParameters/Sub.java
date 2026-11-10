package test;

import java.util.*;

public class Sub extends Base {
    @Override
    public void handle(List<String> items) {
        items.add("a");
    }
}
