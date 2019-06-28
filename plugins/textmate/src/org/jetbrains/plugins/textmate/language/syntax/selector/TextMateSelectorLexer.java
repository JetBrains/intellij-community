package org.jetbrains.plugins.textmate.language.syntax.selector;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TextMateSelectorLexer {
  public static List<TextMateSelectorToken> tokenize(@NotNull String selector) {
    ArrayList<TextMateSelectorToken> result = new ArrayList<>();
    StringBuilder currentSelector = new StringBuilder();
    for (int i = 0; i < selector.length(); i++) {
      char c = selector.charAt(i);
      if (c == '(') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.LPAREN);
      }
      else if (c == ')') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.RPAREN);
      }
      else if (c == ',') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.COMMA);
      }
      else if (c == '|') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.PIPE);
      }
      else if (c == '^') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.HAT);
      }
      else if (c == '-') {
        currentSelector = addPendingToken(result, currentSelector);
        result.add(TextMateSelectorToken.MINUS);
      }
      else if (c == ' ') {
        currentSelector = addPendingToken(result, currentSelector);
      }
      else if ((c == 'R' || c == 'L' || c == 'B') && i + 1 < selector.length() && selector.charAt(i+1) == ':') {
        currentSelector = addPendingToken(result, currentSelector);
        //noinspection AssignmentToForLoopParameter
        i++;
        if (c == 'R') {
          result.add(new PriorityToken(TextMateWeigh.Priority.LOW));
        }
        else if (c == 'L') {
          result.add(new PriorityToken(TextMateWeigh.Priority.LOW));
        }
      }
      else {
        currentSelector.append(c);
      }
    }
    addPendingToken(result, currentSelector);
    return result;
  }

  @NotNull
  private static StringBuilder addPendingToken(ArrayList<TextMateSelectorToken> result, StringBuilder currentSelector) {
    if (currentSelector.length() > 0) {
      result.add(new SelectorToken(currentSelector.toString()));
      return new StringBuilder();
    }
    return currentSelector;
  }

  public static class SignToken implements TextMateSelectorToken {
    private final char mySign;

    public SignToken(char c) {
      mySign = c;
    }

    @Override
    public String toString() {
      return String.valueOf(mySign);
    }
  }

  public static class PriorityToken implements TextMateSelectorToken {
    private final TextMateWeigh.Priority myPriority;

    public PriorityToken(TextMateWeigh.Priority priority) {
      myPriority = priority;
    }

    public TextMateWeigh.Priority getPriority() {
      return myPriority;
    }

    @Override
    public String toString() {
      return myPriority.name();
    }
  }

  public static class SelectorToken implements TextMateSelectorToken {
    private final String myText;

    public SelectorToken(String text) {
      myText = text;
    }

    public String getText() {
      return myText;
    }

    @Override
    public String toString() {
      return myText;
    }
  }
}
