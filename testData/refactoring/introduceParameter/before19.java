import java.util.*;

public class Test {
    int field;
    int method() {
        return <selection>field</selection>;
    }
}

public class Usage {
    int usage(Test[] tests) {
        int sum = 0;
        for(int i = 0; i < tests.length;) {
            sum += tests[i++].method();
        }
        List list = Arrays.asList(tests);
        for(int i = 0; i < list.size();) {
            sum += ((Test)list.get(i++)).method();        
        }
    }
}