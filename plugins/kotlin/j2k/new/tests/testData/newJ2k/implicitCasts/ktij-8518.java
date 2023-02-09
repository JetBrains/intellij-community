public class Test0xFFFF_FFFFL {
    public long readLong(final int index) {
        long l1 = readInt(index);
        long l0 = readInt(index + 4) & 0xFFFFFFFFL;
        return l1 + l0;
    }

    public int readInt(final int index) {
        return 0;
    }
}