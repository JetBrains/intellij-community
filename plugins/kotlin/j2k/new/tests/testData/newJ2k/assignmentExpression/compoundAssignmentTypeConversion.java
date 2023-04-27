public class J {
    public void testField(float f, double d) {
        char c = 1;
        c += f;
        c -= d;
        c *= f;
        c /= d;
        c %= f + d + f;

        // TODO KTIJ-24541
        byte b = 1;
        b += f;
        b -= d;
        b *= f;
        b /= d;
        b %= f + d + f;

        // TODO KTIJ-24541
        short s = 1;
        s += f;
        s -= d;
        s *= f;
        s /= d;
        s %= f + d + f;

        int i = 1;
        i += f;
        i -= d;
        i *= f;
        i /= d;
        i %= f + d + f;

        long l = 1;
        l += f;
        l -= d;
        l *= f;
        l /= d;
        l %= f + d + f;
    }

    public void testArrayAccess(float f, double d) {
        char[] charArr = {1};
        charArr[0] += f;
        charArr[0] -= d;
        charArr[0] *= f;
        charArr[0] /= d;
        charArr[0] %= f + d + f;

        // TODO KTIJ-24541
        byte[] byteArr = {1};
        byteArr[0] += f;
        byteArr[0] -= d;
        byteArr[0] *= f;
        byteArr[0] /= d;
        byteArr[0] %= f + d + f;

        // TODO KTIJ-24541
        short[] shortArr = {1};
        shortArr[0] += f;
        shortArr[0] -= d;
        shortArr[0] *= f;
        shortArr[0] /= d;
        shortArr[0] %= f + d + f;

        int[] intArr = {1};
        intArr[0] += f;
        intArr[0] -= d;
        intArr[0] *= f;
        intArr[0] /= d;
        intArr[0] %= f + d + f;

        long[] longArr = {1};
        longArr[0] += f;
        longArr[0] -= d;
        longArr[0] *= f;
        longArr[0] /= d;
        longArr[0] %= f + d + f;
    }
}