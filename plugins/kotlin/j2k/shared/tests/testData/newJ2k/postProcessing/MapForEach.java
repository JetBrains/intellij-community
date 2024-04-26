// IGNORE_K2

import java.util.HashMap;
import java.util.function.BiConsumer;

class Test {
    void test(HashMap<String, String> map) {
        map.forEach((key, value) -> foo(key, value));
        map.forEach((k, v) -> System.out.println("don't use params"));

        BiConsumer<String, String> biConsumer = new MyBiConsumer();
        map.forEach(biConsumer);
    }

    void foo(String key, String value) {
    }

    class MyBiConsumer implements BiConsumer<String, String> {
        public void accept(String k, String v) {
            System.out.println();
        }
    }
}