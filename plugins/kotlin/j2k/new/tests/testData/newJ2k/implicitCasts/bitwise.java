public class J {
    public void foo(byte b, char c, short s, int i, long l) {
        System.out.println(b >> i);
        System.out.println(b >>> i);
        System.out.println(b << i);
        System.out.println(b << l);
        System.out.println(b << 5);
        System.out.println(5 << b);
        System.out.println(b & b);
        System.out.println(b & 0x01);
        System.out.println(b | b);
        System.out.println(0x01 | b);
        System.out.println(b ^ b);
        System.out.println(5 /*operand '5'*/ << /* left shift */ b /*operand 'b'*/);
        System.out.println(/*operand 'b'*/ b ^ /* xor */ b /*operand 'b'*/);

        System.out.println(c >> i);
        System.out.println(c >>> i);
        System.out.println(c << i);
        System.out.println(c << l);
        System.out.println(c << 5);
        System.out.println(5 << c);
        System.out.println(c & c);
        System.out.println(c & 0x01);
        System.out.println(c | c);
        System.out.println(0x01 | c);
        System.out.println(c ^ c);

        System.out.println(s >> i);
        System.out.println(s >>> i);
        System.out.println(s << i);
        System.out.println(s << l);
        System.out.println(s << 5);
        System.out.println(5 << s);
        System.out.println(s & s);
        System.out.println(s & 0x01);
        System.out.println(s | s);
        System.out.println(0x01 | s);
        System.out.println(s ^ s);

        System.out.println(i >> i);
        System.out.println(i >>> i);
        System.out.println(i << i);
        System.out.println(i << l);
        System.out.println(i << 5);
        System.out.println(5 << i);
        System.out.println(i & i);
        System.out.println(i & 0x01);
        System.out.println(i | i);
        System.out.println(0x01 | i);
        System.out.println(i ^ i);

        System.out.println(l >> i);
        System.out.println(l >>> i);
        System.out.println(l << i);
        System.out.println(l << l);
        System.out.println(l << 5);
        System.out.println(5 << l);
        System.out.println(l & l);
        System.out.println(l & 0x01);
        System.out.println(l | l);
        System.out.println(0x01 | l);
        System.out.println(l ^ l);
    }
}