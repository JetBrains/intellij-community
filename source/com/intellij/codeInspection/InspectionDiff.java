/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jun 21, 2002
 * Time: 7:36:28 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Document;
import org.jdom.Element;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InspectionDiff {
  private static HashMap ourFileToProblem;

  public static void main(String[] args) {
    if (args.length != 3 && args.length != 2) {
      System.out.println("Required parameters: <old_file> <new_file> [<delta_file_name>]");
    }

    String oldPath = args[0];
    String newPath = args[1];
    String outPath = args.length == 3 ? args[2] : null;


    try {
      InputStream oldStream = new BufferedInputStream(new FileInputStream(oldPath));
      InputStream newStream = new BufferedInputStream(new FileInputStream(newPath));

      Document oldDoc = JDOMUtil.loadDocument(oldStream);
      Document newDoc = JDOMUtil.loadDocument(newStream);

      OutputStream outStream = System.out;
      if (outPath != null) {
        outStream = new BufferedOutputStream(new FileOutputStream(outPath));
      }

      Document delta = createDelta(oldDoc, newDoc);
      JDOMUtil.writeDocument(delta, outStream, "\n");
    } catch (Exception e) {
      System.out.println(e);
      e.printStackTrace();
    }
  }

  private static Document createDelta(Document oldDoc, Document newDoc) {
    Element oldRoot = oldDoc.getRootElement();
    Element newRoot = newDoc.getRootElement();


    ourFileToProblem = new HashMap();
    List newProblems = newRoot.getChildren("problem");
    for (Iterator iterator = newProblems.iterator(); iterator.hasNext();) {
      Element newProblem = (Element) iterator.next();
      addProblem(newProblem);
    }

    List oldProblems = oldRoot.getChildren("problem");
    for (Iterator iterator = oldProblems.iterator(); iterator.hasNext();) {
      Element oldProblem = (Element) iterator.next();
      removeIfEquals(oldProblem);
    }

    Element root = new Element("problems");
    Document delta = new Document(root);

    for (Iterator iterator = ourFileToProblem.values().iterator(); iterator.hasNext();) {
      ArrayList fileList = (ArrayList) iterator.next();
      if (fileList != null) {
        for (int i = 0; i < fileList.size(); i++) {
          Element element = (Element) fileList.get(i);
          root.addContent((Element) element.clone());
        }
      }
    }

    return delta;
  }

  private static void removeIfEquals(Element problem) {
    String fileName = problem.getChildText("file");
    ArrayList problemList = (ArrayList) ourFileToProblem.get(fileName);
    if (problemList != null) {
      Element[] problems = (Element[]) problemList.toArray(new Element[problemList.size()]);
      for (int i = 0; i < problems.length; i++) {
        Element toCheck = problems[i];
        if (equals(problem, toCheck)) problemList.remove(toCheck);
      }
    }
  }

  private static void addProblem(Element problem) {
    String fileName = problem.getChildText("file");
    ArrayList problemList = (ArrayList) ourFileToProblem.get(fileName);
    if (problemList == null) {
      problemList = new ArrayList();
      ourFileToProblem.put(fileName, problemList);
    }
    problemList.add(problem);
  }

  private static boolean equals(Element oldProblem, Element newProblem) {
    if (!Comparing.equal(oldProblem.getChildText("class"), newProblem.getChildText("class"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("field"), newProblem.getChildText("field"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("method"), newProblem.getChildText("method"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("constructor"), newProblem.getChildText("constructor"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("interface"), newProblem.getChildText("interface"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("problem_class"), newProblem.getChildText("problem_class"))) return false;
    if (!Comparing.equal(oldProblem.getChildText("description"), newProblem.getChildText("description"))) return false;

    return true;
  }
}
