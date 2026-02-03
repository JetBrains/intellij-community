import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

class AssignmentLocal {
    ArrayList<String> field = new ArrayList<>();

    void test(ArrayList<String> param) {
        ArrayList<String> local = field;
        if (local.isEmpty()) {
            local = initLocal();
        } else {
            local = param;
        }
    }

    private ArrayList<@NotNull String> initLocal() {
        return new ArrayList<>();
    }
}

class AssignmentLocalReverse {
    ArrayList<String> field = new ArrayList<>();

    void test(ArrayList<String> param) {
        ArrayList<@NotNull String> local = field;
        if (local.isEmpty()) {
            local = initLocal();
        } else {
            local = param;
        }
    }

    private ArrayList<String> initLocal() {
        return new ArrayList<>();
    }
}

class AssignmentField {
    ArrayList<String> field = new ArrayList<>();

    void test(ArrayList<String> param) {
        if (field.isEmpty()) {
            field = initField();
        } else {
            field = param;
        }
    }

    private ArrayList<@NotNull String> initField() {
        return new ArrayList<>();
    }
}

class AssignmentFieldReverse {
    ArrayList<@NotNull String> field = new ArrayList<>();

    void test(ArrayList<String> param) {
        if (field.isEmpty()) {
            field = initField();
        } else {
            field = param;
        }
    }

    private ArrayList<String> initField() {
        return new ArrayList<>();
    }
}