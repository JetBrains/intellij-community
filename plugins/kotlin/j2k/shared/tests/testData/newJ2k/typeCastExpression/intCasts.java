class A {
    void intToChar() {
        char a = 1;
        int b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void intToByte() {
        byte a = 1;
        int b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void intToShort() {
        short a = 1;
        int b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void intToLong() {
        long a = 1;
        int b = 2;
        long c = b;
        long d = b + c;
    }

    void intToFloat() {
        float a = 1;
        int b = 2;
        float c = b;
        float d = b + c;
    }

    void intToDouble() {
        double a = 1;
        int b = 2;
        double c = b;
        double d = b + c;
    }

    void charToInt() {
        int a = 'a';
        char b = 'b';
        int c = b;
        int d = b + c;
    }

    void byteToInt() {
        int a = (byte) 1;
        byte b = 2;
        int c = b;
        int d = b + c;
    }

    void shortToInt() {
        int a = (short) 1;
        short b = 2;
        int c = b;
        int d = b + c;
    }

    void longToInt() {
        int a = (int) 1L;
        long b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }

    void floatToInt() {
        int a = (int) 1.0f;
        float b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }

    void doubleToInt() {
        int a = (int) 1.0;
        double b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }
}