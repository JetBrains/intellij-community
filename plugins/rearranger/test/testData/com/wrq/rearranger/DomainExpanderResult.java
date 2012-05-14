package com.carp.casper2.chitchat;

import com.carp.nlp.lexicon.Word;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

/**
 * Gebruikt de synoniemen lijst om onbekende woorden af te beeld op de bekende woorden uit de
 * patterns. Met een synoniemen lijst breid deze de patterns uit met rules om van onbekende woorden
 * op bekende te komen Hij maakt dus het (wiskundige) domein groter omdat er op meer dingen
 * getriggerd wordt, vandaar de naam. Het is een beetje onhandig om door DomainExpander gemaakt
 * rules weer toe te voegen. Voordat je de terminals uit een grammar haalt wil je meestal eerst die
 * synoniemen rules er uit plukken. <br> Je stopt er een hashset met bekende dingen in en je krijgt
 * een mapping terug.
 *
 * @author Oebele van der Veen
 * @version 1.0
 */
public class DomainExpander {
  Vector baselevel = new Vector();


  // idee
  // Er is een baseset waarop geprojecteerd moet worden
  // dit gaat met een reduction graph die iteratief opgebouwd word
  // reductiongraph = vector of level
  // level = vector of (word,backref)
  // baseset word in level 0 gezet en dan wordt elke element bij na gegaan
  // is er een synoniem s van woord x dan krijgt s een ref naar x en komt s een level hoger
  // daarna wordt het volgende level verwerkt.
  // Als er niks meer toegevoegd kan worden, dan wordt bij elke woord uit de baseset alle synoniemen
  // verzameld en wordt er een rule (synoniem1|..|synoniemx) do rewrite(woord) end van gemaakt. (collapsen)
  // mischien frontref ipv backref. Dan gaat het collapsen makkelijker. laten we dat ook doen ja
  // merk op dat er geen dubbelen in de reduction graph mogen voorkomen


  // een node van de reduction tree

  Set noDubArea = new HashSet(); // elk woord gooien we ook hierin (bij reference)


  // Dat is fijn want dan kunnen we snel doublures voorkomen
  Vector reductionGraph = new Vector(); //elk element is een level met nodes
  SynonymList synlist;

  /**
   * Maakt een Domain Expander bij een baseset en synoniemenlijst. Gegeven de bekende woorden van de
   * patterns in de baseset kan deze DomainExpander met de synoniemen nieuwe patterns maken
   *
   * @param baseset een HashSet met de terminals van de patterns
   * @param synlist een synonymList,
   */
  public DomainExpander(Set baseset, SynonymList synlist) {
    // we zetten de basisset op level 0 van de reductionGraph;
    setBase(baseset);
    // even onthouden
    this.synlist = synlist;
  }

  /**
   * Slaat de mapping plat zodat deze weggeschreven kan worden Als er genoeg woorden zijn toegevoegd
   * dan zorgt deze functie er voor dat de data structuur wordt aangepast voor wegschrijven als
   * grammar rules
   */
  public void collapse() {
    // de nodes van de base level hebben nu alle kinderen als hun kinderen
    Vector base = (Vector)reductionGraph.firstElement();
    Node thisnode;
    for (int i = 0; i < base.size(); i++) {
      thisnode = (Node)base.elementAt(i);
      thisnode.children = thisnode.getChildren(); // de kinderen gelijk stellen aan alle kinderen
    }
  }

  /**
   * Voegt meer woorden toe aan de mapping. Dit ding voegt meer woorden toe aan de mapping.
   * expand(3);expand(4) is hetzelfde als expand(7)
   *
   * @param max het maximum aantal hops door de synoniemen vanaf de bekende woorden
   * @return hoeveel hops de laatste woorden echt zijn. Dit kan kleiner zijn dan max als er geen
   *         synoniemen meer zijn.
   */
  public int expand(int max) {
    int curlevels = reductionGraph.size();
    Vector newlevel;
    boolean adding = true; // houd bij of er wat toegevoegd wordt, anders kunnen we ook wel stoppen.
    while ((adding) && (reductionGraph.size() - curlevels) < max) {
      newlevel = expandLevel((Vector)reductionGraph.lastElement());
      reductionGraph.add(newlevel);
      if (newlevel.size() == 0) {
        adding = false;
      }
    }
    return reductionGraph.size() - curlevels;
  }

  private Vector expandLevel(Vector level) {
    System.out.println("Expanding level with " + level.size() + " elements");
    System.out.println("Strings in noDubArea: " + noDubArea.size());
    Vector synonyms;
    Node thisnode, newnode;
    Vector newlevel = new Vector();
    String aword;

    for (int i = 0; i < level.size(); i++) {
      thisnode = ((Node)level.elementAt(i));
      synonyms = synlist.getSynonymsOf(thisnode.word);
      if (synonyms != null) {
        for (int s = 0; s < synonyms.size(); s++) {
          aword = (String)synonyms.elementAt(s);
          if (!noDubArea.contains(aword)) {
            newnode = new Node(aword);  //Node(s) stopts s ook direct in de noDubArea
            newlevel.add(newnode);
            thisnode.addChild(newnode);
          }
        }
      }
    }
    return newlevel;
  }

  private void setBase(Set baseSet) {
    Word word;
    String str;
    System.out.println("Adding base set");
    if (baseSet == null) {
      System.out.println("Given baseset is null");
      return;
    }
    Vector newlevel = new Vector(200);
    Iterator iterator = baseSet.iterator();
    while (iterator.hasNext()) {
      word = (Word)iterator.next();
      if (word != null) {
        str = word.toString();
        if (str != null) {
          newlevel.add(new Node(str));
        }
      }
    }
    reductionGraph.add(newlevel);
    System.out.println("Done");
  }

  /**
   * Schrijf de synoniemen mapping naar rules. Stopt de mapping in de writer
   *
   * @param w een Writer waar de rules heen geschreven worden.
   */
  public void writeToGrammar(Writer w) throws IOException {
    BufferedWriter writer = new BufferedWriter(w);
    String setting = "rule ^setIgnoreHead(true) ^setIgnoreTail(true) ^setMaxIgnored(256) end";
    String commentaar =
      "// Dit bestand is automatisch gegenereert door een DomainExpander object, Het is absoluut slim hier af te blijven.";
    String line;
    Vector base = (Vector)reductionGraph.firstElement();
    Node thisnode;
    try {
      writer.write(commentaar, 0, commentaar.length());
      writer.newLine();
      writer.newLine();
      writer.newLine();
      writer.write(setting, 0, setting.length());
      writer.newLine();
      for (int i = 0; i < base.size(); i++) {
        thisnode = (Node)base.elementAt(i);
        line = thisnode.toParserString();
        if (line.length() > 0) {
          writer.write(line, 0, line.length());
          writer.newLine();
        }
      }
    }
    finally {
      writer.close();
    }
  }

  private class Node {
    private Vector children = new Vector(); // de synonymen van dit woord in een hoger level
    private String word;

    Node(String word) {
      this.word = word;
      noDubArea.add(word); // Kijk stoppen we er gewoon direct in
    }

    public void addChild(Node node) {
      children.add(node);
    }

    //recursief alle kinderen verzamelen
    public Vector getChildren() {
      Vector result = new Vector();
      for (int i = 0; i < children.size(); i++) {
        result.addAll(((Node)children.elementAt(i)).getChildren());
      }
      result.addAll(children);
      return result;
    }

    public String toParserString() {
      // maak een "rule (childer1|childer2...) do rewrite(word) end"
      if (children.size() == 0) {
        return ""; // geen kinderen, geen regel
      }
      if (children.size() == 1) {
        return "rule \"" + ((Node)children.firstElement()).word + "\" do rewrite(" + word + ") end";
      }
      StringBuffer result = new StringBuffer();
      result.append("rule (");
      for (int i = 0; i < children.size(); i++) {
        if (i > 0) {
          result.append("|");
        }
        result.append("\"");
        result.append(((Node)children.elementAt(i)).word);
        result.append("\"");
      }
      result.append(") do rewrite(" + word + ") end");
      return result.toString();
    }
  }
}
