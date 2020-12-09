import java.util.Optional;

public class BuilderWithSetterPrefixAndExistedMethods {
    private final byte[] secret;
    private Optional<String> name;

    BuilderWithSetterPrefixAndExistedMethods(byte[] secret, Optional<String> name) {
        this.secret = secret;
        this.name = name;
    }

    public static Builder builder() {
        return new Builder();
    }

    public byte[] getSecret() {
        return this.secret;
    }

    public Optional<String> getName() {
        return this.name;
    }

    public void setName(Optional<String> name) {
        this.name = name;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof BuilderWithSetterPrefixAndExistedMethods)) return false;
        final BuilderWithSetterPrefixAndExistedMethods other = (BuilderWithSetterPrefixAndExistedMethods) o;
        if (!other.canEqual((Object) this)) return false;
        if (!java.util.Arrays.equals(this.getSecret(), other.getSecret())) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof BuilderWithSetterPrefixAndExistedMethods;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + java.util.Arrays.hashCode(this.getSecret());
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        return result;
    }

    public String toString() {
        return "BuilderWithSetterPrefixAndExistedMethods(secret=" + java.util.Arrays.toString(this.getSecret()) + ", name=" + this.getName() + ")";
    }

    public Builder toBuilder() {
        return new Builder().withSecret(this.secret).withName(this.name);
    }

    public static class Builder {
        private byte[] secret;
        private Optional<String> name;

        Builder() {
        }

        public Builder withSecret(String value) {
            secret = value.getBytes();
            return this;
        }

        public Builder withSecret(byte[] value) {
            secret = value;
            return this;
        }

        public Builder withName(String name) {
            this.name = Optional.of(name);
            return this;
        }

        public Builder withName(Optional<String> name) {
            this.name = name;
            return this;
        }

        public BuilderWithSetterPrefixAndExistedMethods build() {
            return new BuilderWithSetterPrefixAndExistedMethods(secret, name);
        }

        public String toString() {
            return "BuilderWithSetterPrefixAndExistedMethods.Builder(secret=" + java.util.Arrays.toString(this.secret) + ", name=" + this.name + ")";
        }
    }

    public static void main(String[] args) {
        BuilderWithSetterPrefixAndExistedMethods obj = BuilderWithSetterPrefixAndExistedMethods.builder().withSecret("Secret").withName(Optional.of("aaa")).build();
        BuilderWithSetterPrefixAndExistedMethods rtn = obj.toBuilder().build();
        System.out.println(rtn);
    }
}
