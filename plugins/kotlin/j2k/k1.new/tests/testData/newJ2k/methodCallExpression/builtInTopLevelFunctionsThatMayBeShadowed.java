import java.util.*;

class A {
    void println(String s) {
        System.out.println(s);
    }
    void print(String s) {
        System.out.print(s);
    }
    <T> void emptyList() {
        List<String> list = Collections.emptyList();
    }
    <T> void emptySet() {
        Set<String> set = Collections.emptySet();
    }
    <K, V> void emptyMap() {
        Map<String, String> map = Collections.emptyMap();
    }
    <T> void listOf(String s) {
        List<String> list = Collections.singletonList(s);
    }
    <T> void setOf(String s) {
        Set<String> set = Collections.singleton(s);
    }
}