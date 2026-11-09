import java.util.*;

class A {
    List<String> fieldChangedInKotlin = new ArrayList<>();
    List<String> fieldReadInJava = new ArrayList<>();

    List<String> changedInKotlin() {
        return new ArrayList<>();
    }

    List<String> readInJava() {
        return new ArrayList<>();
    }
}
