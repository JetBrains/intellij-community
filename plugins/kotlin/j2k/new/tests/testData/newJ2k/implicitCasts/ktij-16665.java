public class J {
    private static int byteToInt(byte[] b) {
        int i = 0, result = 0, shift = 0;
        while (i < b.length) {
            byte be = b[i];
            result |= (be & 0xff) << shift;
            shift += 8;
            i += 1;
        }
        return result;
    }
}