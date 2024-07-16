public class Foo {
    private long LONG_MASK;

    private int mulsubBorrow(int[] q, int[] a, int x, int len, int offset) {
        long xLong = x & LONG_MASK;
        long carry = 0;
        offset += len;
        for (int j=len-1; j >= 0; j--) {
            long product = (a[j] & LONG_MASK) * xLong + carry;
            long difference = q[offset--] - product;
            carry = (product >>> 32)
                    + (((difference & LONG_MASK) >
                        (((~(int)product) & LONG_MASK))) ? 1:0);
        }
        return (int)carry;
    }
}