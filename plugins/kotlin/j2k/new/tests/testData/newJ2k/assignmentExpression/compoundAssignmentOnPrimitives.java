public class J {
    public void testField(char c, byte b, short s, int i, long l, float f, double d) {
        char cc = 1;
        cc += c;
        cc += b;
        cc += s;
        cc += i;
        cc += l;
        cc += f;
        cc += d;
        cc -= d;
        cc *= f;
        cc /= d;
        cc %= f + d + f;
        //
        byte bb = 1;
        bb += c;
        bb += b;
        bb += s;
        bb += i;
        bb += l;
        bb += f;
        bb += d;
        bb -= d;
        bb *= f;
        bb /= d;
        bb %= f + d + f;
        //
        short ss = 1;
        ss += c;
        ss += b;
        ss += s;
        ss += i;
        ss += l;
        ss += f;
        ss += d;
        ss -= d;
        ss *= f;
        ss /= d;
        ss %= f + d + f;
        //
        int ii = 1;
        ii += c;
        ii += b;
        ii += s;
        ii += i;
        ii += l;
        ii += f;
        ii += d;
        ii -= d;
        ii *= f;
        ii /= d;
        ii %= f + d + f;
        //
        long ll = 1;
        ll += c;
        ll += b;
        ll += s;
        ll += i;
        ll += l;
        ll += f;
        ll += d;
        ll -= d;
        ll *= f;
        ll /= d;
        ll %= f + d + f;
        //
        float ff = 1;
        ff += c;
        ff += b;
        ff += s;
        ff += i;
        ff += l;
        ff += f;
        ff += d;
        ff -= d;
        ff *= f;
        ff /= d;
        ff %= f + d + f;
        //
        double dd = 1;
        dd += c;
        dd += b;
        dd += s;
        dd += i;
        dd += l;
        dd += f;
        dd += d;
        dd -= d;
        dd *= f;
        dd /= d;
        dd %= f + d + f;
    }

    public void testArrayAccess(char c, byte b, short s, int i, long l, float f, double d) {
        // in K1 this currently results in a NO_SET_METHOD error (KT-11272), which should be fixed in K2
        char[] charArr = {1};
        // <KT-11272>
        charArr[0] += c;
        charArr[0] += b;
        charArr[0] += s;
        charArr[0] += i;
        charArr[0] += l;
        // </KT-11272>
        charArr[0] += f;
        charArr[0] += d;
        charArr[0] -= d;
        charArr[0] *= f;
        charArr[0] /= d;
        charArr[0] %= f + d + f;
        //
        byte[] byteArr = {1};
        byteArr[0] += c;
        byteArr[0] += b;
        byteArr[0] += s;
        byteArr[0] += i;
        byteArr[0] += l;
        byteArr[0] += f;
        byteArr[0] += d;
        byteArr[0] -= d;
        byteArr[0] *= f;
        byteArr[0] /= d;
        byteArr[0] %= f + d + f;
        //
        short[] shortArr = {1};
        shortArr[0] += c;
        shortArr[0] += b;
        shortArr[0] += s;
        shortArr[0] += i;
        shortArr[0] += l;
        shortArr[0] += f;
        shortArr[0] += d;
        shortArr[0] -= d;
        shortArr[0] *= f;
        shortArr[0] /= d;
        shortArr[0] %= f + d + f;
        //
        int[] intArr = {1};
        intArr[0] += c;
        intArr[0] += b;
        intArr[0] += s;
        intArr[0] += i;
        intArr[0] += l;
        intArr[0] += f;
        intArr[0] += d;
        intArr[0] -= d;
        intArr[0] *= f;
        intArr[0] /= d;
        intArr[0] %= f + d + f;
        //
        long[] longArr = {1};
        longArr[0] += c;
        longArr[0] += b;
        longArr[0] += s;
        longArr[0] += i;
        longArr[0] += l;
        longArr[0] += f;
        longArr[0] += d;
        longArr[0] -= d;
        longArr[0] *= f;
        longArr[0] /= d;
        longArr[0] %= f + d + f;
        //
        float[] floatArr = {1};
        floatArr[0] += c;
        floatArr[0] += b;
        floatArr[0] += s;
        floatArr[0] += i;
        floatArr[0] += l;
        floatArr[0] += f;
        floatArr[0] += d;
        floatArr[0] -= d;
        floatArr[0] *= f;
        floatArr[0] /= d;
        floatArr[0] %= f + d + f;
        //
        double[] doubleArr = {1};
        doubleArr[0] += c;
        doubleArr[0] += b;
        doubleArr[0] += s;
        doubleArr[0] += i;
        doubleArr[0] += l;
        doubleArr[0] += f;
        doubleArr[0] += d;
        doubleArr[0] -= d;
        doubleArr[0] *= f;
        doubleArr[0] /= d;
        doubleArr[0] %= f + d + f;
    }
}