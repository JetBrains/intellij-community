public class AssignTest { 
    public static void main(String[] args) {
        int foo = 0;
 
        while (true) {
            if (foo != 12) System.out.println("Test");
            if (Math.random() > 0.5) foo = 22;
        }
    }
}
