import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@com.fasterxml.jackson.annotation.JsonRootName("RootName")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties("someFloat")
public class BuilderCopyableAnnotations {

    @javax.persistence.Column(name = "dont_copy_1")
    private float someFloat;

    @javax.persistence.Column(name = "dont_copy_2")
    @com.fasterxml.jackson.annotation.JsonAlias("someAlias")
    private int someInt;

    @javax.persistence.Column(name = "dont_copy_3")
    @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
    private String someField;

    @javax.persistence.Column(name = "dont_copy_4")
    @com.fasterxml.jackson.annotation.JsonAnySetter
    private java.util.List<String> someStrings;

    BuilderCopyableAnnotations(float someFloat, int someInt, String someField, List<String> someStrings) {
        this.someFloat = someFloat;
        this.someInt = someInt;
        this.someField = someField;
        this.someStrings = someStrings;
    }

    public static BuilderCopyableAnnotationsBuilder builder() {
        return new BuilderCopyableAnnotationsBuilder();
    }

    public static class BuilderCopyableAnnotationsBuilder {
        private float someFloat;
        private int someInt;
        private String someField;
        private ArrayList<String> someStrings;

        BuilderCopyableAnnotationsBuilder() {
        }

        public BuilderCopyableAnnotationsBuilder someFloat(float someFloat) {
            this.someFloat = someFloat;
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonAlias("someAlias")
        public BuilderCopyableAnnotationsBuilder someInt(int someInt) {
            this.someInt = someInt;
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonProperty(value = "someProperty", required = true)
        public BuilderCopyableAnnotationsBuilder someField(String someField) {
            this.someField = someField;
            return this;
        }

        @com.fasterxml.jackson.annotation.JsonAnySetter
        public BuilderCopyableAnnotationsBuilder someString(String someString) {
            if (this.someStrings == null) this.someStrings = new ArrayList<String>();
            this.someStrings.add(someString);
            return this;
        }

        public BuilderCopyableAnnotationsBuilder someStrings(Collection<? extends String> someStrings) {
            if (someStrings == null) {
                throw new NullPointerException("someStrings cannot be null");
            }
            if (this.someStrings == null) this.someStrings = new ArrayList<String>();
            this.someStrings.addAll(someStrings);
            return this;
        }

        public BuilderCopyableAnnotationsBuilder clearSomeStrings() {
            if (this.someStrings != null)
                this.someStrings.clear();
            return this;
        }

        public BuilderCopyableAnnotations build() {
            java.util.List<String> someStrings;
            switch (this.someStrings == null ? 0 : this.someStrings.size()) {
                case 0:
                    someStrings = java.util.Collections.emptyList();
                    break;
                case 1:
                    someStrings = java.util.Collections.singletonList(this.someStrings.get(0));
                    break;
                default:
                    someStrings = java.util.Collections.unmodifiableList(new ArrayList<String>(this.someStrings));
            }

            return new BuilderCopyableAnnotations(this.someFloat, this.someInt, this.someField, someStrings);
        }

        public String toString() {
            return "BuilderCopyableAnnotations.BuilderCopyableAnnotationsBuilder(someFloat=" + this.someFloat + ", someInt=" + this.someInt + ", someField=" + this.someField + ", someStrings=" + this.someStrings + ")";
        }
    }
}