import java.util.*;

class TestCollection {
    public Collection<String> stringsField = new ArrayList<>();

    public void field() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    public void param(Collection<String> strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }

    public void local() {
        Collection<String> stringsLocal = new ArrayList<>();
        for (String s : stringsLocal) {
            System.out.println(s.length());
        }
    }
}

class TestIterable {
    public Iterable<String> stringsField = new ArrayList<>();

    public void field() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    public void param(Iterable<String> strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }

    public void local() {
        Iterable<String> stringsLocal = new ArrayList<>();
        for (String s : stringsLocal) {
            System.out.println(s.length());
        }
    }
}

class TestList {
    public List<String> stringsField = new ArrayList<>();

    public void field() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    public void param(List<String> strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }

    public void local() {
        List<String> stringsLocal = new ArrayList<>();
        for (String s : stringsLocal) {
            System.out.println(s.length());
        }
    }
}

class TestSet {
    public Set<String> stringsField = new HashSet<>();

    public void field() {
        for (String s : stringsField) {
            System.out.println(s.length());
        }
    }

    public void param(Set<String> strings) {
        for (String s : strings) {
            System.out.println(s.length());
        }
    }

    public void local() {
        Set<String> stringsLocal = new HashSet<>();
        for (String s : stringsLocal) {
            System.out.println(s.length());
        }
    }
}