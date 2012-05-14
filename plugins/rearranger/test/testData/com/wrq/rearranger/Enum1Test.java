/**
 * $id
 * Author: Dave Kriewall
 * Created: Oct 20, 2005
 * Copyright 2004, WRQ, Inc.
 */
package com.wrq.rearranger.testClasses;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;

import javax.swing.tree.TreePath;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA. User: JJohnson Date: Aug 30, 2005 Time: 11:06:54 AM To change this template use File |
 * Settings | File Templates.
 */
public class Enum1Test implements Cloneable {
  private static final String STEREOTYPE_PREFIX = "STEREOTYPE_";

  public void setExpectedReceipt(byte[] newValue) {
  }

  public void setParameter(String value) {
  }

  public void setStorage(SuiteItem suiteItem) {
  }

  // ------------------------------ FIELDS ------------------------------
  public enum ExecutionState {
    PASSED, FAILED, UNTESTED
  }

  private Enum1Test parent = null;
  private SuiteModel suiteModel;
  private SuiteItem  storage;
  private List<Enum1Test> childNodes = new ArrayList<Enum1Test>(10);

  // --------------------------- CONSTRUCTORS ---------------------------
  public Enum1Test(SuiteModel model, Stereotype type) {
  }

  // --------------------- GETTER / SETTER METHODS ---------------------
  private void setStereotype(Stereotype type) {
  }

  public Stereotype getStereotype() {
    return null;
  }

  public static Stereotype getStereotype(String stStr) {
    return null;
  }

  public long getDelay() {
    return storage.getDelay();
  }

  public InstructionItem getInstructionItem() {
    return storage.getInstructionItem();
  }

  public int getIterations() {
    return storage.getIterations();
  }

  public Date getLastTested() {
    Date date = storage.getLastTested();
    return date == null ? null : date;
  }

  public String getName() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return getInstruction(getInstructionItem().getInstructionRef()).getName();
    }
    return storage.getName();
  }

  public Enum1Test getParent() {
    return parent;
  }

  // ------------------------ CANONICAL METHODS ------------------------
  public Object clone() throws CloneNotSupportedException {
    try {
      return super.clone();
    }
    catch (final CloneNotSupportedException e) {
      return null;
    }
  }

  public String toString() {
    return getName();
  }

  // -------------------------- OTHER METHODS --------------------------
  public void add(Enum1Test appendingNode, boolean createStorage) {
    childNodes.add(appendingNode);
    appendingNode.setParent(this, createStorage);
  }

  public void add(Enum1Test insertingNode, Enum1Test selection) {
    childNodes.add(childNodes.indexOf(selection) + 1, insertingNode);
    insertingNode.setParent(this, true);
  }

  public void add(int index, Enum1Test t) {
    t.setParent(this, true);
    childNodes.add(index, t);
  }

  public void setParent(Enum1Test parent, boolean createStorage) {
    Enum1Test oldParent = this.parent;
    this.parent = parent;
    if (createStorage) {
      parent.addChild(this);
    }
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_PARENT, oldParent, parent, this);
  }

  private void addChild(Enum1Test childNode) {
    storage.addSuiteItem(childNode.getStorage());
  }

  public SuiteItem getStorage() {
    return storage;
  }

  public boolean canMove(Direction direction) {
    switch (direction) {
      case UP:
        return (getNextUp() != null);
      case DOWN:
        return (getNextDown() != null);
    }
    return false;
  }

  public Enum1Test getNextUp() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_ROOT)) {
      return null;
    }
    Enum1Test nextUpNode;
    if (isFirstChild()) {
//            nextUpNode = getParent().getNextUp();
      nextUpNode = getParent().getNextDown();
    }
    else {
      int newIndexUp = indexOf() - 1;
      nextUpNode = (Enum1Test)getParent().getChild(newIndexUp);
    }
    return nextUpNode;
  }

  public boolean isFirstChild() {
    return indexOf() == 0;
  }

  /** Get index of this node within its parents children list. */
  public int indexOf() {
    Enum1Test parent = getParent();
    if (parent == null) {
      return 0;
    }
    return parent.indexOf(this);
  }

  public Object getChild(int index) {
    return childNodes.get(index);
  }

  public Enum1Test getNextDown() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_ROOT)) {
      return null;
    }
    Enum1Test nextDownNode;
    if (isLastChild()) {
//            nextDownNode = getParent().getNextDown();
      nextDownNode = getParent().getNextUp();
    }
    else {
      int newIndexDown = indexOf() + 1;
      nextDownNode = (Enum1Test)getParent().getChild(newIndexDown);
    }
    return nextDownNode;
  }

  public boolean isLastChild() {
    if (parent == null) {
      return true;  // root is an only-child
    }
    int indexOf = indexOf();
    return (indexOf == parent.getChildCount() - 1);
  }

  public int getChildCount() {
    return childNodes.size();
  }

  public Enum1Test[] getChildren() {
    return childNodes.toArray(new Enum1Test[]{});
  }

  public String getDescription() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return getInstruction(getInstructionItem().getInstructionRef()).getDescription();
    }
    return storage.getDescription();
  }

  public long getDuration() {
    long durationSum = 0;
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      durationSum += storage.getDuration();
    }
    else {
      for (Enum1Test t : childNodes) {
        durationSum += t.getDuration();
      }
    }
    return durationSum;
  }

  public String getParameter() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return getInstructionItem().getParameter();
    }
    return "";
  }

  public TreePath getPath() {
    List<Enum1Test> parents = new ArrayList<Enum1Test>(20);
    for (Enum1Test node = this; node != null; node = node.getParent()) {
      parents.add(node);
    }
    Collections.reverse(parents);
    return new TreePath(parents.toArray(new Enum1Test[0]));
  }

  public String getReceiptString() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return Utilities.toString(getInstructionItem().getReceipt());
    }
    return "";
  }

  public ExecutionState getState() {
    ExecutionState state = ExecutionState.UNTESTED;
    if (storage.getInstructionItem() != null) {
      byte[] receipt = getInstructionItem().getReceipt();
      if (receipt != null) {
        byte[] expectedReceipt = getInstructionItem().getExpectedReceipt();
        if (expectedReceipt == null) {
          state = ExecutionState.FAILED;
        }
        else if (receipt.length != expectedReceipt.length) {
          state = ExecutionState.FAILED;
        }
        else {
          state = ExecutionState.PASSED;
          for (int i = 0; i < receipt.length; i++) {
            if (receipt[i] != expectedReceipt[i]) {
              state = ExecutionState.FAILED;
              break;
            }
          }
        }
      }
    }
    return state;
  }

  public String getStateText() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      ExecutionState state = getState();
      switch (state) {
        case UNTESTED:
          return "Untested";
        case PASSED:
          return "Passed";
        case FAILED:
          return "Failed";
      }
    }
    return this.getPassed() + " / " + this.getFailed() + " of " + this.getTotalInstructions();
  }

  public int getPassed() {
    int count = 0;
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      if (getState().equals(ExecutionState.PASSED)) {
        count++;
      }
    }
    else {
      for (Enum1Test t : childNodes) {
        count += t.getPassed();
      }
    }
    return count;
  }

  public int getFailed() {
    int count = 0;
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      if (getState().equals(ExecutionState.FAILED)) {
        count++;
      }
    }
    else {
      for (Enum1Test t : childNodes) {
        count += t.getFailed();
      }
    }
    return count;
  }

  public int getTotalInstructions() {
    int count = 0;
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      count++;
    }
    for (Enum1Test t : childNodes) {
      if (!getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
        count += t.getTotalInstructions();
      }
    }
    return count;
  }

  public String getToolTip() {
    StringBuilder tt = new StringBuilder(200);
    tt.append("<HTML>");
    switch (getStereotype()) {
      case STEREOTYPE_ROOT:
        buildRootToolTip(tt);
        break;
      case STEREOTYPE_GROUP:
        buildGroupToolTip(tt);
        break;
      case STEREOTYPE_INSTRUCTION:
        buildInstructionToolTip(tt);
        break;
    }
    buildRunInfoToolTip(tt);
    tt.append("</HTML>");
    return tt.toString();
  }

  private void buildRunInfoToolTip(StringBuilder tt) {
    tt.append("<hr>");  // a divider line
    tt.append("Iterations: ").append(getIterations()).append("<p>");
    tt.append("Pause: ").append(getDelay()).append("<p>");
    tt.append("State: ").append(getStateText()).append("<p>");    // todo add state color getStatecolor()...
    tt.append("Duration: ").append(getDuration()).append("<p>");
    tt.append("Executed: ").append(getLastTested()).append("<p>");
  }

  private void buildInstructionToolTip(StringBuilder tt) {
    InstructionItem inst = getInstructionItem();
    if (inst != null) {
      String id = inst.getInstructionRef();
      Instruction instruction = getInstruction(id);
      tt.append("Command: ").append(instruction.getCommand()).append("<p>");
    }
    tt.append("Parameter: ").append(getParameter()).append("<p>");
    tt.append("Received: ").append(getReceiptString()).append("<p>");
    tt.append("Expected: ").append(getExpectedReceiptString()).append("<p>");
  }

  private String getExpectedReceiptString() {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return Utilities.toString(getInstructionItem().getExpectedReceipt());
    }
    return "";
  }

  private void buildGroupToolTip(StringBuilder tt) {
    String desc = getDescription();
    if (desc != null && desc.length() > 0) {
      tt.append(desc).append("<p>");
    }
  }

  private void buildRootToolTip(StringBuilder tt) {
    String author = suiteModel.getAuthor();
    String desc = getDescription();
    String filePath = suiteModel.hasStorage() ? suiteModel.getStorageFile().getAbsolutePath() : null;
    if (author != null && author.length() > 0) {
      tt.append(author).append("<p>");
    }
    if (desc != null && desc.length() > 0) {
      tt.append(desc).append("<p>");
    }
    if (filePath != null && filePath.length() > 0) {
      tt.append(filePath).append("<p>");
    }
  }

  public boolean hasChildren() {
    return childNodes.size() > 0;
  }

  public int indexOf(Enum1Test t) {
    return childNodes.indexOf(t);
  }

  public boolean isGroup() {
    return getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION);
  }

  public void remove(Enum1Test node) {
    childNodes.remove(node);
    // node.setParent(null);
  }

  public void setDelay(long delay) {
    long oldDelay = storage.getDelay();
    storage.setDelay(delay);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_DELAY, oldDelay, delay, this);
  }

  public void setDescription(String description) {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return;  // name is fixed for instructions
    }
    String oldDescription = storage.getDescription();
    storage.setDescription(description);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_DESC, oldDescription, description, this);
  }

  public void setDuration(long duration) {
    if (!getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return;  // name is fixed anything execept instructions
    }
    long oldDuration = getDuration();
    storage.setDuration(duration);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_DURATION, oldDuration, duration, this);
  }

  public void setInstructionItem(InstructionItem instruction) {
    InstructionItem oldInstruction = this.getInstructionItem();
    storage.setInstructionItem(instruction);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_INSTRUCTION, oldInstruction, instruction, this);
  }

  public void setIterations(int iterations) {
    int oldIterations = storage.getIterations();
    storage.setIterations(iterations);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_ITERATIONS, oldIterations, iterations, this);
  }

  public void setLastTested(Date lastTested) {
    Date oldLastTested = getLastTested();
    storage.setLastTested(new Date());
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_LAST_TESTED, oldLastTested, lastTested, this);
  }

  public void setName(String name) {
    if (getStereotype().equals(Stereotype.STEREOTYPE_INSTRUCTION)) {
      return;  // name is fixed for instructions
    }
    String oldName = storage.getName();
    storage.setName(name);
    suiteModel.fireChangeEvent(SuiteModel.EVT_TOPIC_NAME, oldName, name, this);
  }

  // -------------------------- INNER CLASSES --------------------------
  public enum Stereotype {
    STEREOTYPE_ROOT, STEREOTYPE_GROUP, STEREOTYPE_INSTRUCTION
  }

  public enum Direction {
    UP, DOWN
  }

  public void execute() {
    // Repeat this node -iterations- times
    for (int repeatCount = 0; repeatCount < getIterations(); repeatCount++) {
      if (getStereotype().equals(Enum1Test.Stereotype.STEREOTYPE_INSTRUCTION)) {
        executeInstruction();
      }
      else {
        executeChildren();
      }
      // Pause after each execution this number of milliseconds before continuing
      try {
        Thread.sleep(getDelay());
      }
      catch (InterruptedException e) {
        e.printStackTrace();  // todo - logger
      }
    }
  }

  private void executeChildren() {
    Enum1Test[] children = getChildren();
    for (Enum1Test node : children) {
      node.execute();  // recursive execution for nested groups of instructions.
    }
  }

  private void executeInstruction() {
  }

  private ByteBuffer getPacketToSend(InstructionItem instruction) {
    return null;
  }

  public ByteBuffer getPacketToSend() {
    return getPacketToSend(getInstructionItem());
  }

  public String getStateText(boolean firstCap) {
    String stateText;
    switch (getState()) {
      case UNTESTED:
        stateText = "untested";
        break;
      case PASSED:
        stateText = "passed";
        break;
      case FAILED:
        stateText = "failed";
        break;
      default:
        stateText = "** unknown **";
    }
    return stateText;
  }

  public Instruction getInstruction(String id) {
    return null;
  }

  public Instruction getInstruction() {
    String id = this.getInstructionItem().getInstructionRef();
    return getInstruction(id);
  }
}

class InstructionItem {
  public byte[] getExpectedReceipt() {
    return new byte[0];  //To change body of created methods use File | Settings | File Templates.
  }

  public void setExpectedReceipt(byte[] newValue) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public String getParameter() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public void setParameter(String value) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public String getInstructionRef() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public byte[] getReceipt() {
    return null;
  }
}

class Instruction {
  public String getName() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public String getDescription() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public String getCommand() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }
}

class SuiteModel {
  public static final Object EVT_TOPIC_EXPECTED_RECEIPT = "";
  public static final Object EVT_TOPIC_PARAMETER        = "";
  public static final Object EVT_TOPIC_PARENT           = "";
  public static final Object EVT_TOPIC_DELAY            = "";
  public static       Object EVT_TOPIC_DESC             = "";
  public static       Object EVT_TOPIC_DURATION         = "";
  public static       Object EVT_TOPIC_INSTRUCTION      = "";
  public static       Object EVT_TOPIC_ITERATIONS       = "";
  public static       Object EVT_TOPIC_LAST_TESTED      = "";
  public static       Object EVT_TOPIC_NAME             = "";

  public void fireChangeEvent(Object p0, Object oldValue, Object newValue, Enum1Test testEnum) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public String getAuthor() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public boolean hasStorage() {
    return false;  //To change body of created methods use File | Settings | File Templates.
  }

  public File getStorageFile() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public DataFlowRunner getInstructionSet() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }
}

class SuiteItem {
  public void setIterations(int i) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setStereotype(String s) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public String getStereotype() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public long getDelay() {
    return 0;  //To change body of created methods use File | Settings | File Templates.
  }

  public InstructionItem getInstructionItem() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public int getIterations() {
    return 0;  //To change body of created methods use File | Settings | File Templates.
  }

  public Date getLastTested() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public String getName() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public void addSuiteItem(SuiteItem storage) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public String getDescription() {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }

  public long getDuration() {
    return 0;  //To change body of created methods use File | Settings | File Templates.
  }

  public void setDelay(long delay) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setDescription(String description) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setDuration(long duration) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setInstructionItem(InstructionItem instruction) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setLastTested(Date date) {
    //To change body of created methods use File | Settings | File Templates.
  }

  public void setName(String name) {
    //To change body of created methods use File | Settings | File Templates.
  }
}

class Utilities {
  public static String toString(Object receipt) {
    return null;  //To change body of created methods use File | Settings | File Templates.
  }
}