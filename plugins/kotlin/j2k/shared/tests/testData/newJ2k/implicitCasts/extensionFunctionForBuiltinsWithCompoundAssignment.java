import java.lang.Math;

import static java.lang.Math.max;

public class JJ {
    void foo() {
        int sum = 0;

        sum += Math.pow(1, 2);
        sum += Math.pow(1.0, 2.0);
        sum += max(1, 2);
        sum += max(1.0, 2.0);
    }
}