import java.util.ArrayList;

class C {
    void foo(ArrayList<Integer> list) {
        int[] result = new int[] {0};
        list.forEach(integer -> result[0] += integer);
    }
}