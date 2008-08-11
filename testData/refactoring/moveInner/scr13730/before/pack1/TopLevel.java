package pack1;

public class TopLevel { 
    public static class StaticInner {
        public class NonStaticInnerInner {
            private String name;
            
            public NonStaticInnerInner(String name) {
                this.name = name;
            }
            
            public String toString() {
                return name;
            }
        }
    }
}