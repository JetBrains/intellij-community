package test;

class Immutable {
    public static class Builder
    {
        public long getValue() {
            return 0;
        }

        public Builder setValue(long value_) {
            return this;
        }
    }
}
