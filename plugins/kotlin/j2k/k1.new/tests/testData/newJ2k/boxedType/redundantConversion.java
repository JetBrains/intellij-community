public class J {
    public static void foo(Boolean bool, Character c, Byte b, Short s, Integer i, Long l, Float f, Double d, Object obj) {
        System.out.println(bool.booleanValue());
        System.out.println(c.charValue());
        System.out.println(b.byteValue());
        System.out.println(s.shortValue());
        System.out.println(i.intValue());
        System.out.println(l.longValue());
        System.out.println(f.floatValue());
        System.out.println(d.doubleValue());

        if (obj instanceof Boolean) {
            System.out.println("Boolean: " + ((Boolean) obj).booleanValue());
        } else if (obj instanceof Character) {
            System.out.println("Character: " + ((Character) obj).charValue());
        } else if (obj instanceof Byte) {
            System.out.println("Byte: " + ((Byte) obj).byteValue());
        } else if (obj instanceof Short) {
            System.out.println("Short: " + ((Short) obj).shortValue());
        } else if (obj instanceof Integer) {
            System.out.println("Integer: " + ((Integer) obj).intValue());
        } else if (obj instanceof Long) {
            System.out.println("Long: " + ((Long) obj).longValue());
        } else if (obj instanceof Float) {
            System.out.println("Float: " + ((Float) obj).floatValue());
        } else if (obj instanceof Double) {
            System.out.println("Double: " + ((Double) obj).doubleValue());
        }
    }
}
