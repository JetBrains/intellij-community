import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

class ReturnMethodCallExpr {
    @NotNull String test(ArrayList<String> param) {
        return param.get(0);
    }
}