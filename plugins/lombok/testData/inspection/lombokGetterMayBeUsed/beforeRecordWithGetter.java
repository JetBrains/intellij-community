// "Use lombok @Getter for 'UserId'" "false"

import lombok.Getter;

record UserId<caret>(@Getter Long value) {

}
