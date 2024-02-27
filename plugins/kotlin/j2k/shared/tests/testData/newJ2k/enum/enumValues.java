// RUNTIME_WITH_FULL_JDK

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.stream.Stream;

enum MyEnum {
    A,
    B;

    @ExperimentalStdlibApi
    void internalTest() {
        MyEnum x = values()[1];
        MyEnum[] y = values();
    }
}

class EnumTest {
    void replaceWithEntries() {
        MyEnum x = MyEnum.values()[1];

        // Array methods suitable for List
        int z = MyEnum.values().length;

        // References
        for (MyEnum value : MyEnum.values()) {}
    }

    void replaceWithEntriesWithConversionToArray() {
        // Simple call
        MyEnum[] x = MyEnum.values();

        // Object methods
        String o = MyEnum.values().toString();

        // Operator method
        MyEnum.values()[1] = MyEnum.A;

        // Array methods not suitable for List
        MyEnum.values().clone();
        String y = Arrays.toString(MyEnum.values());
        Array.get(MyEnum.values(), 1);

        // Stream methods
        Stream<MyEnum> s = Arrays.stream(MyEnum.values());

        // Iterator methods
        Iterator<MyEnum> i1 = Arrays.stream(MyEnum.values()).iterator();
        Spliterator<MyEnum> i2 = Arrays.stream(MyEnum.values()).spliterator();

        // This case is not handled
        List<MyEnum> z = List.of(MyEnum.values());

    }
}