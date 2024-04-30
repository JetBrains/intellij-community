class A {
    void longToChar() {
        char a = (char) 1L;
        long b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void longToByte() {
        byte a = (byte) 1L;
        long b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void longToShort() {
        short a = (short) 1L;
        long b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void longToInt() {
        int a = (int) 1L;
        long b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }

    void longToFloat() {
        float a = 1L;
        long b = 2;
        float c = b;
        float d = b + c;
    }

    void longToDouble() {
        double a = 1L;
        long b = 2;
        double c = b;
        double d = b + c;
    }

    void charToLong() {
        long a = 'a';
        char b = 'b';
        long c = b;
        long d = b + c;
    }

    void byteToLong() {
        long a = (byte) 1;
        byte b = 2;
        long c = b;
        long d = b + c;
    }

    void shortToLong() {
        long a = (short) 1;
        short b = 2;
        long c = b;
        long d = b + c;
    }

    void intToLong() {
        long a = (int) 1;
        int b = 2;
        long c = b;
        long d = b + c;
    }

    void floatToLong() {
        long a = (long) 1.0f;
        float b = 2;
        long c = (long) b;
        long d = (long) (b + c);
    }

    void doubleToLong() {
        long a = (long) 1.0;
        double b = 2;
        long c = (long) b;
        long d = (long) (b + c);
    }
}