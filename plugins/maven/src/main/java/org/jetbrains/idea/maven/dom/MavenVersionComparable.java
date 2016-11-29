package org.jetbrains.idea.maven.dom;

import java.math.BigInteger;
import java.util.*;

/**
 * Copy pasted from org.apache.maven.artifact.versioning.ComparableVersion
 *
 * @author Sergey Evdokimov
 */
public class MavenVersionComparable implements Comparable<MavenVersionComparable> {
  private String value;

  private String canonical;

  private ListItem items;

  private interface Item {
    int INTEGER_ITEM = 0;
    int STRING_ITEM = 1;
    int LIST_ITEM = 2;

    int compareTo(Item item);

    int getType();

    boolean isNull();
  }

  /**
   * Represents a numeric item in the version item list.
   */
  private static class IntegerItem
    implements Item {
    private static final BigInteger BigInteger_ZERO = new BigInteger("0");

    private final BigInteger value;

    public static final IntegerItem ZERO = new IntegerItem();

    private IntegerItem() {
      this.value = BigInteger_ZERO;
    }

    public IntegerItem(String str) {
      this.value = new BigInteger(str);
    }

    public int getType() {
      return INTEGER_ITEM;
    }

    public boolean isNull() {
      return BigInteger_ZERO.equals(value);
    }

    public int compareTo(Item item) {
      if (item == null) {
        return BigInteger_ZERO.equals(value) ? 0 : 1; // 1.0 == 1, 1.1 > 1
      }

      switch (item.getType()) {
        case INTEGER_ITEM:
          return value.compareTo(((IntegerItem)item).value);

        case STRING_ITEM:
          return 1; // 1.1 > 1-sp

        case LIST_ITEM:
          return 1; // 1.1 > 1-1

        default:
          throw new RuntimeException("invalid item: " + item.getClass());
      }
    }

    public String toString() {
      return value.toString();
    }
  }

  /**
   * Represents a string in the version item list, usually a qualifier.
   */
  private static class StringItem
    implements Item {
    private static final String[] QUALIFIERS = {"alpha", "beta", "milestone", "rc", "snapshot", "", "sp"};

    private static final List<String> _QUALIFIERS = Arrays.asList(QUALIFIERS);

    private static final Properties ALIASES = new Properties();

    static {
      ALIASES.setProperty("ga", "");
      ALIASES.setProperty("final", "");
      ALIASES.setProperty("cr", "rc");
    }

    /**
     * A comparable value for the empty-string qualifier. This one is used to determine if a given qualifier makes
     * the version older than one without a qualifier, or more recent.
     */
    private static final String RELEASE_VERSION_INDEX = String.valueOf(_QUALIFIERS.indexOf(""));

    private String value;

    public StringItem(String value, boolean followedByDigit) {
      if (followedByDigit && value.length() == 1) {
        // a1 = alpha-1, b1 = beta-1, m1 = milestone-1
        switch (value.charAt(0)) {
          case 'a':
            value = "alpha";
            break;
          case 'b':
            value = "beta";
            break;
          case 'm':
            value = "milestone";
            break;
        }
      }
      this.value = ALIASES.getProperty(value, value);
    }

    public int getType() {
      return STRING_ITEM;
    }

    public boolean isNull() {
      return (comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX) == 0);
    }

    /**
     * Returns a comparable value for a qualifier.
     * <p/>
     * This method both takes into account the ordering of known qualifiers as well as lexical ordering for unknown
     * qualifiers.
     * <p/>
     * just returning an Integer with the index here is faster, but requires a lot of if/then/else to check for -1
     * or QUALIFIERS.size and then resort to lexical ordering. Most comparisons are decided by the first character,
     * so this is still fast. If more characters are needed then it requires a lexical sort anyway.
     *
     * @param qualifier
     * @return an equivalent value that can be used with lexical comparison
     */
    public static String comparableQualifier(String qualifier) {
      int i = _QUALIFIERS.indexOf(qualifier);

      return i == -1 ? _QUALIFIERS.size() + "-" + qualifier : String.valueOf(i);
    }

    public int compareTo(Item item) {
      if (item == null) {
        // 1-rc < 1, 1-ga > 1
        return comparableQualifier(value).compareTo(RELEASE_VERSION_INDEX);
      }
      switch (item.getType()) {
        case INTEGER_ITEM:
          return -1; // 1.any < 1.1 ?

        case STRING_ITEM:
          return comparableQualifier(value).compareTo(comparableQualifier(((StringItem)item).value));

        case LIST_ITEM:
          return -1; // 1.any < 1-1

        default:
          throw new RuntimeException("invalid item: " + item.getClass());
      }
    }

    public String toString() {
      return value;
    }
  }

  /**
   * Represents a version list item. This class is used both for the global item list and for sub-lists (which start
   * with '-(number)' in the version specification).
   */
  private static class ListItem
    extends ArrayList<Item>
    implements Item {
    public int getType() {
      return LIST_ITEM;
    }

    public boolean isNull() {
      return (size() == 0);
    }

    void normalize() {
      for (ListIterator<Item> iterator = listIterator(size()); iterator.hasPrevious(); ) {
        Item item = iterator.previous();
        if (item.isNull()) {
          iterator.remove(); // remove null trailing items: 0, "", empty list
        }
        else {
          break;
        }
      }
    }

    public int compareTo(Item item) {
      if (item == null) {
        if (size() == 0) {
          return 0; // 1-0 = 1- (normalize) = 1
        }
        Item first = get(0);
        return first.compareTo(null);
      }
      switch (item.getType()) {
        case INTEGER_ITEM:
          return -1; // 1-1 < 1.0.x

        case STRING_ITEM:
          return 1; // 1-1 > 1-sp

        case LIST_ITEM:
          Iterator<Item> left = iterator();
          Iterator<Item> right = ((ListItem)item).iterator();

          while (left.hasNext() || right.hasNext()) {
            Item l = left.hasNext() ? left.next() : null;
            Item r = right.hasNext() ? right.next() : null;

            // if this is shorter, then invert the compare and mul with -1
            int result = l == null ? -1 * r.compareTo(l) : l.compareTo(r);

            if (result != 0) {
              return result;
            }
          }

          return 0;

        default:
          throw new RuntimeException("invalid item: " + item.getClass());
      }
    }

    public String toString() {
      StringBuilder buffer = new StringBuilder("(");
      for (Iterator<Item> iter = iterator(); iter.hasNext(); ) {
        buffer.append(iter.next());
        if (iter.hasNext()) {
          buffer.append(',');
        }
      }
      buffer.append(')');
      return buffer.toString();
    }
  }

  public MavenVersionComparable(String version) {
    parseVersion(version);
  }

  public final void parseVersion(String version) {
    this.value = version;

    items = new ListItem();

    version = version.toLowerCase(Locale.ENGLISH);

    ListItem list = items;

    Stack<Item> stack = new Stack<>();
    stack.push(list);

    boolean isDigit = false;

    int startIndex = 0;

    for (int i = 0; i < version.length(); i++) {
      char c = version.charAt(i);

      if (c == '.') {
        if (i == startIndex) {
          list.add(IntegerItem.ZERO);
        }
        else {
          list.add(parseItem(isDigit, version.substring(startIndex, i)));
        }
        startIndex = i + 1;
      }
      else if (c == '-') {
        if (i == startIndex) {
          list.add(IntegerItem.ZERO);
        }
        else {
          list.add(parseItem(isDigit, version.substring(startIndex, i)));
        }
        startIndex = i + 1;

        if (isDigit) {
          list.normalize(); // 1.0-* = 1-*

          if ((i + 1 < version.length()) && Character.isDigit(version.charAt(i + 1))) {
            // new ListItem only if previous were digits and new char is a digit,
            // ie need to differentiate only 1.1 from 1-1
            list.add(list = new ListItem());

            stack.push(list);
          }
        }
      }
      else if (Character.isDigit(c)) {
        if (!isDigit && i > startIndex) {
          list.add(new StringItem(version.substring(startIndex, i), true));
          startIndex = i;
        }

        isDigit = true;
      }
      else {
        if (isDigit && i > startIndex) {
          list.add(parseItem(true, version.substring(startIndex, i)));
          startIndex = i;
        }

        isDigit = false;
      }
    }

    if (version.length() > startIndex) {
      list.add(parseItem(isDigit, version.substring(startIndex)));
    }

    while (!stack.isEmpty()) {
      list = (ListItem)stack.pop();
      list.normalize();
    }

    canonical = items.toString();
  }

  private static Item parseItem(boolean isDigit, String buf) {
    return isDigit ? new IntegerItem(buf) : new StringItem(buf, false);
  }

  public int compareTo(MavenVersionComparable o) {
    return items.compareTo(o.items);
  }

  public String toString() {
    return value;
  }

  public boolean equals(Object o) {
    return (o instanceof MavenVersionComparable) && canonical.equals(((MavenVersionComparable)o).canonical);
  }

  public int hashCode() {
    return canonical.hashCode();
  }
}
