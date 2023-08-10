import java.util.*;

class Test
{
    public static void testSet4() {
        Set tenL = getNumberSet4(10);
        Set fiveL = getNumberSet4(5);

        tenL.removeAll(fiveL);
    }

    public static Set getNumberSet4(int n) {
        Set result;
        for (int i = 0; i < n; i++) {
            result.add(i);
        }
        return result;
    }
}