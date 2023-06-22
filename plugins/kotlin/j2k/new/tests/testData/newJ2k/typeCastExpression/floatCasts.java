class A {
    void floatToChar() {
        char a = (char) 1.0f;
        float b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void floatToByte() {
        byte a = (byte) 1.0f;
        float b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void floatToShort() {
        short a = (short) 1.0f;
        float b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void floatToInt() {
        int a = (int) 1.0f;
        float b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }

    void floatToLong() {
        long a = (long) 1.0f;
        float b = 2;
        long c = (long) b;
        long d = (long) (b + c);
    }

    void floatToDouble() {
        double a = 1.0f;
        float b = 2;
        double c = b;
        double d = b + c;
    }

    void charToFloat() {
        float a = 'a';
        char b = 'b';
        float c = b;
        float d = b + c;
    }

    void byteToFloat() {
        float a = (byte) 1;
        byte b = 2;
        float c = b;
        float d = b + c;
    }

    void shortToFloat() {
        float a = (short) 1;
        short b = 2;
        float c = b;
        float d = b + c;
    }

    void intToFloat() {
        float a = 1;
        int b = 2;
        float c = (float) b;
        float d = (float) (b + c);
    }

    void longToFloat() {
        float a = (float) 1L;
        long b = 2;
        float c = (float) b;
        float d = (float) (b + c);
    }

    void doubleToFloat() {
        float a = (float) 1.0;
        double b = 2;
        float c = (float) b;
        float d = (float) (b + c);
    }
}