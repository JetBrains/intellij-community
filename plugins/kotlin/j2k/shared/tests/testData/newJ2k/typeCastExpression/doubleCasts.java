class A {
    void doubleToChar() {
        char a = (char) 1.0;
        double b = 2;
        char c = (char) b;
        char d = (char) (b + c);
    }

    void doubleToByte() {
        byte a = (byte) 1.0;
        double b = 2;
        byte c = (byte) b;
        byte d = (byte) (b + c);
    }

    void doubleToShort() {
        short a = (short) 1.0;
        double b = 2;
        short c = (short) b;
        short d = (short) (b + c);
    }

    void doubleToInt() {
        int a = (int) 1.0;
        double b = 2;
        int c = (int) b;
        int d = (int) (b + c);
    }

    void doubleToLong() {
        long a = (long) 1.0;
        double b = 2;
        long c = (long) b;
        long d = (long) (b + c);
    }

    void doubleToFloat() {
        float a = (float) 1.0;
        double b = 2;
        float c = (float) b;
        float d = (float) (b + c);
    }

    void charToDouble() {
        double a = 'a';
        char b = 'b';
        double c = b;
        double d = b + c;
    }

    void byteToDouble() {
        double a = (byte) 1;
        byte b = 2;
        double c = b;
        double d = b + c;
    }

    void shortToDouble() {
        double a = (short) 1;
        short b = 2;
        double c = b;
        double d = b + c;
    }

    void intToDouble() {
        double a = 1;
        int b = 2;
        double c = b;
        double d = b + c;
    }

    void longToDouble() {
        double a = (double) 1L;
        long b = 2;
        double c = (double) b;
        double d = (double) (b + c);
    }

    void floatToDouble() {
        double a = 1.0f;
        float b = 2;
        double c = b;
        double d = b + c;
    }
}