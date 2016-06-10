class Exception {
}
public class BuilderConstructorException {

    private int i;

    public BuilderConstructorException(int i) throws Exception {
        System.out.println("sss");
    }

    public static void main(String[] args) {
        try {
            builder().i(2).build();
        } catch (Exception ignore) {
        }
    }

    public static BuilderConstructorExceptionBuilder builder() {
        return new BuilderConstructorExceptionBuilder();
    }

    public static class BuilderConstructorExceptionBuilder {
        private int i;

        BuilderConstructorExceptionBuilder() {
        }

        public BuilderConstructorExceptionBuilder i(int i) {
            this.i = i;
            return this;
        }

        public BuilderConstructorException build() throws Exception {
            return new BuilderConstructorException(i);
        }

        public String toString() {
            return "BuilderConstructorException.BuilderConstructorExceptionBuilder(i=" + this.i + ")";
        }
    }
}
