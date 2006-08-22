package com.intellij.cyclicDependencies;

import com.intellij.compiler.Chunk;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;

import java.util.*;

/**
 * User: anna
 * Date: Feb 10, 2005
 */
public class CyclicDependenciesUtil{
  public static <Node> List<Chunk<Node>> buildChunks(Graph<Node> graph) {
    final DFSTBuilder<Node> dfstBuilder = new DFSTBuilder<Node>(graph);
    final TIntArrayList sccs = dfstBuilder.getSCCs();
    final List<Chunk<Node>> chunks = new ArrayList<Chunk<Node>>();
    sccs.forEach(new TIntProcedure() {
      int myTNumber = 0;
      public boolean execute(int size) {
        Set<Node> packs = new LinkedHashSet<Node>();
        for (int j = 0; j < size; j++) {
          packs.add(dfstBuilder.getNodeByTNumber(myTNumber + j));
        }
        chunks.add(new Chunk<Node>(packs));
        myTNumber += size;
        return true;
      }
    });

    return chunks;
  }


  public static class Path <Node> {
    private ArrayList<Node> myPath = new ArrayList<Node>();

    public Path() {
    }

    public Path(Path<Node> path) {
      myPath = new ArrayList<Node>(path.myPath);
    }

    public Node getBeg() {
      return myPath.get(0);
    }

    public Node getEnd() {
      return myPath.get(myPath.size() - 1);
    }

    public boolean contains(Node node) {
      return myPath.contains(node);
    }

    public List<Node> getNextNodes(Node node) {
      List<Node> result = new ArrayList<Node>();
      for (int i = 0; i < myPath.size() - 1; i++) {
        Node nodeN = myPath.get(i);
        if (nodeN == node) {
          result.add(myPath.get(i + 1));
        }
      }
      return result;
    }

    public void add(Node node) {
      myPath.add(node);
    }

    public ArrayList<Node> getPath() {
      return myPath;
    }
  }

  public static class GraphTraverser<Node> {
    private List<Path<Node>> myCurrentPaths = new ArrayList<Path<Node>>();
    private Node myBegin;
    private Chunk<Node> myChunk;
    private int myMaxPathsCount;
    private Graph<Node> myGraph;

    public GraphTraverser(final Node begin, final Chunk<Node> chunk, final int maxPathsCount, final Graph<Node> graph) {
      myBegin = begin;
      myChunk = chunk;
      myMaxPathsCount = maxPathsCount;
      myGraph = graph;
    }

    public Set<Path<Node>> traverse() {
      Set<Path<Node>> result = new HashSet<Path<Node>>();
      Path<Node> firstPath = new Path<Node>();
      firstPath.add(myBegin);
      myCurrentPaths.add(firstPath);
      while (!myCurrentPaths.isEmpty() && result.size() < myMaxPathsCount) {
        final Path<Node> path = myCurrentPaths.get(0);
        final Set<Node> nextNodes = getNextNodes(path.getEnd());
        nextStep(nextNodes, path, result);
      }
      return result;
    }

    public Set<ArrayList<Node>> convert(Set<Path<Node>> paths) {
      Set<ArrayList<Node>> result = new HashSet<ArrayList<Node>>();
      for (Path<Node> path : paths) {
        result.add(path.getPath());
      }
      return result;
    }

    private void nextStep(final Set<Node> nextNodes, final Path<Node> path, Set<Path<Node>> result) {
      myCurrentPaths.remove(path);
      for (Node node : nextNodes) {
        if (path.getEnd() == node) {
          continue;
        }
        if (path.getBeg() == node) {
          result.add(path);
          continue;
        }
        Path<Node> newPath = new Path<Node>(path);
        newPath.add(node);
        if (path.contains(node)) {
          final Set<Node> nodesAfterInnerCycle = getNextNodes(node);
          nodesAfterInnerCycle.removeAll(path.getNextNodes(node));
          nextStep(nodesAfterInnerCycle, newPath, result);
        }
        else {
          myCurrentPaths.add(newPath);
        }
      }
    }

    private Set<Node> getNextNodes(Node node) {
      Set<Node> result = new HashSet<Node>();
      final Iterator<Node> in = myGraph.getIn(node);
      for (; in.hasNext();) {
        final Node inNode = in.next();
        if (myChunk.containsNode(inNode)) {
          result.add(inNode);
        }
      }
      return result;
    }
  }
}
