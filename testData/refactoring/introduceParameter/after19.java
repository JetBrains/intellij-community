import java.util.*;

public class Test {
    int field;
    int method(int anObject) {
        return anObject;
    }
}

public class Usage {
    int usage(Test[] tests) {
        int sum = 0;
        for(int i = 0; i < tests.length;) {
            final Test test = tests[i++];
            sum += test.method(test.field);
        }
        List list = Arrays.asList(tests);
        for(int i = 0; i < list.size();) {
            final Test test = (Test) list.get(i++);
            sum += test.method(test.field);        
        }
    }
}