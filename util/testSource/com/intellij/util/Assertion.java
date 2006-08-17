package com.intellij.util;

import com.intellij.openapi.util.Comparing;
import gnu.trove.Equality;
import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public class Assertion extends Assert {
  private StringConvertion myStringConvertion;
  private Equality myEquality = Equality.CANONICAL;

  public Assertion() {
    this(StringConvertion.DEFAULT);
  }

  public Assertion(StringConvertion stringConvertion) {
    myStringConvertion = stringConvertion;
  }

  public void setStringConvertion(StringConvertion stringConvertion) {
    myStringConvertion = stringConvertion;
  }

  public StringConvertion getStringConvertion() { return myStringConvertion; }

  public Equality getEquality() { return myEquality; }

  public void compareAll(Object[] expected, Object[] actual) {
    checkNotNulls(expected, actual);
    String expectedLines = converToLines(expected);
    String actualLines = converToLines(actual);
    Assert.assertEquals(expectedLines, actualLines);
    Assert.assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      checkEquals("Index=" + i, expected[i], actual[i]);
    }
  }

  private void checkNotNulls(Object[] expected, Object[] actual) {
    Assert.assertNotNull("Expected is null", expected);
    Assert.assertNotNull("Actual is null", actual);
  }

  public void compareAll(Object[][] expected, Object[][] actual) {
    checkNotNulls(expected, actual);
    Assert.assertEquals(convertToLines(expected), convertToLines(actual));
    Assert.assertEquals(expected.length, actual.length);
    for (int i = 0; i < expected.length; i++) {
      compareAll(expected[i], actual[i]);
    }
  }

  private String convertToLines(Object[][] expected) {
    StringBuffer expectedLines = new StringBuffer();
    for (int i = 0; i < expected.length; i++) {
      Object[] objects = expected[i];
      expectedLines.append(concatenateAsStrings(objects, " "));
      expectedLines.append("\n");
    }
    return expectedLines.toString();
  }

  private void checkEquals(String message, Object expected, Object actual) {
    Assert.assertTrue(message +
               " expected:<" + convertToString(expected) +
               "> actual:" + convertToString(actual) + ">",
               myEquality.equals(expected, actual));
  }

  public String converToLines(Object[] objects) {
    return concatenateAsStrings(objects, "\n");
  }

  private String concatenateAsStrings(Object[] objects, String separator) {
    StringBuffer buffer = new StringBuffer();
    String lineEnd = "";
    for (int i = 0; i < objects.length; i++) {
      Object object = objects[i];
      buffer.append(lineEnd);
      buffer.append(convertToString(object));
      lineEnd = separator;
    }
    String reference = buffer.toString();
    return reference;
  }

  public void enumerate(Object[] objects) {
    for (int i = 0; i < objects.length; i++) {
      Object object = objects[i];
      System.out.println("[" + i + "] = " + convertToString(object));
    }
  }

  public void enumerate(Collection objects) {
    enumerate(objects.toArray());
  }

  private String convertToString(Object object) {
    if (object == null) return "null";
    return myStringConvertion.convert(object);
  }

  public void compareAll(Object[] expected, List actual) {
    compareAll(expected, actual.toArray());
  }

  public void compareAll(List expected, Object[] actual) {
    compareAll(expected.toArray(), actual);
  }

  public void compareUnordered(Object[] expected, Collection actual) {
    assertEquals(expected.length, actual.size());
    for (Object exp : expected) {
      assertTrue(actual.contains(exp));
    }
    //ArrayList expectedList = new ArrayList(Arrays.asList(new Object[Math.max(actual.size(), expected.length)]));
    //ArrayList actualList = new ArrayList(actual);
    //for (int i = 0; i < expected.length; i++) {
    //  Object object = expected[i];
    //  int index = actualList.indexOf(object);
    //  if (index == -1) index = i;
    //  expectedList.set(index, object);
    //}
    //compareAll(expectedList, actualList);
  }

  public void compareUnordered(Collection expected, Collection actual) {
    compareUnordered(expected.toArray(), actual);
  }

  public void compareUnordered(Collection expected, Object[] actual) {
    compareUnordered(expected, new ArrayList(Arrays.asList(actual)));
  }

  public void compareAll(List expected, List actual) {
    compareAll(expected, actual.toArray());
  }

  public static void compareLines(String text, String[] lines) throws IOException {
    BufferedReader reader = new BufferedReader(new StringReader(text));
    for (int i = 0; i < lines.length - 1; i++)
      Assert.assertEquals(lines[i], reader.readLine());
    String lastLine = lines[lines.length - 1];
    char[] buffer = new char[lastLine.length()];
    reader.read(buffer, 0, buffer.length);
    Assert.assertEquals(lastLine, new String(buffer));
    Assert.assertEquals(-1, reader.read());
  }

  public void contains(Collection collection, Object object) {
    if (collection.contains(object))
      return;
    compareAll(new Object[]{object}, collection.toArray());
    Assert.assertTrue(collection.contains(object));
  }

  public void contains(Object[] array, Object object) {
    contains(Arrays.asList(array), object);
  }

  public void singleElement(Collection collection, Object object) {
    compareAll(new Object[]{object}, collection.toArray());
    Assert.assertEquals(1, collection.size());
    checkEquals("", object, collection.iterator().next());
  }

  public void empty(Object[] array) {
    try {
      compareAll(ArrayUtil.EMPTY_OBJECT_ARRAY, array);
    } catch(AssertionFailedError e) {
      System.err.println("Size: " + array.length);
      throw e;
    }
  }

  public void empty(Collection objects) {
    empty(objects.toArray());
  }

  public void count(int count, Collection objects) {
    if (count != objects.size()) {
      empty(objects);
    }
    Assert.assertEquals(count, objects.size());
  }

  public void empty(int[] ints) {
    Object[] objects = new Object[ints.length];
    for (int i = 0; i < ints.length; i++) {
      objects[i] = new Integer(ints[i]);
    }
  }

  public void singleElement(Object[] objects, Object element) {
    singleElement(Arrays.asList(objects), element);
  }

  public void count(int number, Object[] objects) {
    count(number, Arrays.asList(objects));
  }

  public void compareUnordered(Object[] expected, Object[] actual) {
    compareUnordered(expected, new HashSet(Arrays.asList(actual)));
  }

  public void compareAll(int[] expected, int[] actual) {
    compareAll(asObjectArray(expected), asObjectArray(actual));
  }

  private static Object[] asObjectArray(int[] ints) {
    Object[] result = new Object[ints.length];
    for (int i = 0; i < ints.length; i++) {
      int anInt = ints[i];
      result[i] = new Integer(anInt);
    }
    return result;
  }

  public void setEquality(Equality equality) {
    myEquality = equality;
  }

  public void singleElement(int[] actual, int element) {
    compareAll(new int[]{element}, actual);
  }

  public void size(int size, Collection collection) {
    if (collection.size() != size) {
      System.err.println("Expected: " + size + " actual: " + collection.size());
      compareUnordered(ArrayUtil.EMPTY_OBJECT_ARRAY, collection);
    }
    Assert.assertEquals(size, collection.size());
  }

  public void containsAll(Object[] array, Collection subCollection) {
    containsAll(Arrays.asList(array), subCollection);
  }

  public void containsAll(Collection list, Collection subCollection) {
    if (list.containsAll(subCollection)) return;
    for (Iterator iterator = subCollection.iterator(); iterator.hasNext();) {
      Object item = iterator.next();
      boolean isContained = false;
      for (Iterator iterator1 = list.iterator(); iterator1.hasNext();) {
        Object superSetItem = iterator1.next();
        if (myEquality.equals(superSetItem, item)) {
          isContained = true;
          break;
        }
      }
      Assert.assertTrue(myStringConvertion.convert(item), isContained);
    }
  }

  public <T> void singleOccurence(Collection<T> collection, T item) {
    int number = countOccurences(collection, item);
    if (number != 1) {
      enumerate(collection);
      Assert.fail(myStringConvertion.convert(item) + "\n occured " + number + " times");
    }
  }

  public <T> int countOccurences(Collection<T> collection, T item) {
    int counter = 0;
    for (Iterator<T> iterator = collection.iterator(); iterator.hasNext();) {
      T obj = iterator.next();
      if (Comparing.equal(item, obj)) counter++;
    }
    return counter;
  }

  public void containsAll(Collection collection, Object[] subArray) {
    containsAll(collection, Arrays.asList(subArray));
  }

  public void size(int size, Object[] objects) {
    size(size, Arrays.asList(objects));
  }

  public void containsAll(Object[] array, Object[] subArray) {
    containsAll(array, Arrays.asList(subArray));
  }

  public void compareAll(char[] expected, char[] actual) {
    compareAll(asObjectArray(expected), asObjectArray(actual));
  }

  private Object[] asObjectArray(char[] chars) {
    Object[] array = new Object[chars.length];
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      array[i] = new Character(c);
    }
    return array;
  }
}
