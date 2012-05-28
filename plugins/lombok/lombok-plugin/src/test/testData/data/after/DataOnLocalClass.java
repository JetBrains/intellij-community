class DataOnLocalClass1 {


  public static void main(String[] args) {

    class Local {
      final int x;
      String name;

      @java.lang.SuppressWarnings("all")
      public Local(final int x) {
        this.x = x;
      }

      @java.lang.SuppressWarnings("all")
      public int getX() {
        return this.x;
      }

      @java.lang.SuppressWarnings("all")
      public String getName() {
        return this.name;
      }

      @java.lang.SuppressWarnings("all")
      public void setName(final String name) {
        this.name = name;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof Local)) return false;
        final Local other = (Local) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.getX() != other.getX()) return false;
        if (this.getName() == null ? other.getName() != null : !this.getName().equals((java.lang.Object) other.getName())) return false;
        return true;
      }

      @java.lang.SuppressWarnings("all")
      public boolean canEqual(final java.lang.Object other) {
        return other instanceof Local;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = result * PRIME + this.getX();
        result = result * PRIME + (this.getName() == null ? 0 : this.getName().hashCode());
        return result;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public java.lang.String toString() {
        return "Local(x=" + this.getX() + ", name=" + this.getName() + ")";
      }
    }
  }
}

class DataOnLocalClass2 {

  {

    class Local {
      final int x;

      class InnerLocal {
        @lombok.NonNull
        String name;

        @java.lang.SuppressWarnings("all")
        public InnerLocal(@lombok.NonNull final String name) {
          if (name == null) throw new java.lang.NullPointerException("name");
          this.name = name;
        }

        @lombok.NonNull
        @java.lang.SuppressWarnings("all")
        public String getName() {
          return this.name;
        }

        @java.lang.SuppressWarnings("all")
        public void setName(@lombok.NonNull final String name) {
          if (name == null) throw new java.lang.NullPointerException("name");
          this.name = name;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public boolean equals(final java.lang.Object o) {
          if (o == this) return true;
          if (!(o instanceof InnerLocal)) return false;
          final InnerLocal other = (InnerLocal) o;
          if (!other.canEqual((java.lang.Object) this)) return false;
          if (this.getName() == null ? other.getName() != null : !this.getName().equals((java.lang.Object) other.getName())) return false;
          return true;
        }

        @java.lang.SuppressWarnings("all")
        public boolean canEqual(final java.lang.Object other) {
          return other instanceof InnerLocal;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public int hashCode() {
          final int PRIME = 31;
          int result = 1;
          result = result * PRIME + (this.getName() == null ? 0 : this.getName().hashCode());
          return result;
        }

        @java.lang.Override
        @java.lang.SuppressWarnings("all")
        public java.lang.String toString() {
          return "Local.InnerLocal(name=" + this.getName() + ")";
        }
      }

      @java.lang.SuppressWarnings("all")
      public Local(final int x) {
        this.x = x;
      }

      @java.lang.SuppressWarnings("all")
      public int getX() {
        return this.x;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public boolean equals(final java.lang.Object o) {
        if (o == this) return true;
        if (!(o instanceof Local)) return false;
        final Local other = (Local) o;
        if (!other.canEqual((java.lang.Object) this)) return false;
        if (this.getX() != other.getX()) return false;
        return true;
      }

      @java.lang.SuppressWarnings("all")
      public boolean canEqual(final java.lang.Object other) {
        return other instanceof Local;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public int hashCode() {
        final int PRIME = 31;
        int result = 1;
        result = result * PRIME + this.getX();
        return result;
      }

      @java.lang.Override
      @java.lang.SuppressWarnings("all")
      public java.lang.String toString() {
        return "Local(x=" + this.getX() + ")";
      }
    }
  }
}