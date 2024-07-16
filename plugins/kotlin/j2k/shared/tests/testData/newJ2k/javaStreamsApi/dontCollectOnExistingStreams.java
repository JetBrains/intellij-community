// IGNORE_K2

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;


class Test {
    public void main(List<String> lst) {
        Stream<Integer> stream = Stream.of(1);
        List<Integer> list = stream.collect(Collectors.toList());
    }
}