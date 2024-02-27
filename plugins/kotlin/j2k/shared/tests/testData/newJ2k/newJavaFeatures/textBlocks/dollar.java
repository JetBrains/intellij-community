public class J {
    public static void main(String[] args) {
        String s = """
                $""";
        String s2 = """
                ${'$'}""";
        String dollar1 = """
                $a""";
        String dollar2 = """
                $A""";
        String dollar3 = """
                ${s}""";
        String dollar4 = """
                $$""";
    }
}