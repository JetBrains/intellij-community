package com.intellij.cyclicDependencies;

import com.intellij.analysis.AnalysisScope;
import com.intellij.compiler.Chunk;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.packageDependencies.ForwardDependenciesBuilder;
import com.intellij.psi.*;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.DFSTBuilder;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;

import java.util.*;

/**
 * User: anna
 * Date: Jan 30, 2005
 */
public class CyclicDependenciesBuilder{
  private final Project myProject;
  private final AnalysisScope myScope;
  private Map<String, PsiPackage> myPackages = new HashMap<String, PsiPackage>();
  private Graph<PsiPackage> myGraph;
  private Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> myFilesInDependentPackages = new HashMap<PsiPackage, Map<PsiPackage, Set<PsiFile>>>();
  private Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> myBackwardFilesInDependentPackages = new HashMap<PsiPackage, Map<PsiPackage, Set<PsiFile>>>();
  private Map<PsiPackage, Set<PsiPackage>> myPackageDependencies = new HashMap<PsiPackage, Set<PsiPackage>>();
  private HashMap<PsiPackage, Set<ArrayList<PsiPackage>>> myCyclicDependencies = new HashMap<PsiPackage, Set<ArrayList<PsiPackage>>>();
  private int myFileCount = 0;
  private ForwardDependenciesBuilder myForwardBuilder;
  private int myPerPackageCycleCount;

  private String myRootNodeNameInUsageView;

  public CyclicDependenciesBuilder(final Project project, final AnalysisScope scope, int perPackageCycleCount) {
    myProject = project;
    myScope = scope;
    myForwardBuilder = new ForwardDependenciesBuilder(myProject, myScope){
      public String getRootNodeNameInUsageView() {
        return CyclicDependenciesBuilder.this.getRootNodeNameInUsageView();
      }

      public String getInitialUsagesPosition() {
        return "Select package to analyze from the left tree";
      }
    };
    myPerPackageCycleCount = perPackageCycleCount;
  }

  public String getRootNodeNameInUsageView() {
    return myRootNodeNameInUsageView;
  }

  public void setRootNodeNameInUsageView(final String rootNodeNameInUsageView) {
    myRootNodeNameInUsageView = rootNodeNameInUsageView;
  }

  public Project getProject() {
    return myProject;
  }

  public AnalysisScope getScope() {
    return myScope;
  }

  public ForwardDependenciesBuilder getForwardBuilder() {
    return myForwardBuilder;
  }

  public void analyze() {
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(getProject()).getFileIndex();
    getScope().accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {

      }

      public void visitFile(PsiFile file) {
        if (file != null && file instanceof PsiJavaFile) {
          PsiJavaFile psiJavaFile = (PsiJavaFile)file;
          if (getScope().contains(psiJavaFile)) {
            final PsiPackage aPackage = findPackage(psiJavaFile.getPackageName());
            if (aPackage != null) {
              myPackages.put(psiJavaFile.getPackageName(), aPackage);
            }
          }
          final Set<PsiPackage> packs = getPackageHierarhy(psiJavaFile.getPackageName());
          final ForwardDependenciesBuilder builder = new ForwardDependenciesBuilder(getProject(),
                                                                                    new AnalysisScope(psiJavaFile,
                                                                                                      AnalysisScope.SOURCE_JAVA_FILES));
          builder.setTotalFileCount(getScope().getFileCount());
          builder.setInitialFileCount(++myFileCount);
          builder.analyze();
          final Set<PsiFile> psiFiles = builder.getDependencies().get(psiJavaFile);
          for (Iterator<PsiPackage> iterator = packs.iterator(); iterator.hasNext();) {
            PsiPackage pack = iterator.next();
            Set<PsiPackage> pack2Packages = myPackageDependencies.get(pack);
            if (pack2Packages == null) {
              pack2Packages = new HashSet<PsiPackage>();
              myPackageDependencies.put(pack, pack2Packages);
            }
            for (Iterator<PsiFile> it = psiFiles.iterator(); it.hasNext();) {
              PsiFile psiFile = it.next();
              if (!(psiFile instanceof PsiJavaFile) ||
                  !projectFileIndex.isInSourceContent(psiFile.getVirtualFile()) ||
                  !getScope().contains(psiFile)) {
                continue;
              }

              // construct dependent packages
              final String packageName = ((PsiJavaFile)psiFile).getPackageName();
              //do not depend on parent packages
              if (packageName == null || packageName.startsWith(pack.getQualifiedName())) {
                continue;
              }
              final Set<PsiPackage> depPackages = getPackageHierarhy(packageName);
              if (depPackages.isEmpty()) { //not from analyze scope
                continue;
              }
              pack2Packages.addAll(depPackages);

              for (Iterator<PsiPackage> depIt = depPackages.iterator(); depIt.hasNext();) {
                PsiPackage depPackage = depIt.next();
                constractFilesInDependenciesPackagesMap(pack, depPackage, psiFile, myFilesInDependentPackages);
                constractFilesInDependenciesPackagesMap(depPackage, pack, psiJavaFile, myBackwardFilesInDependentPackages);
              }
              
              constractWholeDependenciesMap(psiJavaFile, psiFile);
            }
          }
        }
      }
    });
    ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      if (indicator.isCanceled()) {
        throw new ProcessCanceledException();
      }
      indicator.setText("Building dependencies graph");
      indicator.setText2("");
      indicator.setIndeterminate(true);
    }
    myCyclicDependencies = getCycles(myPackages.values(), myPerPackageCycleCount);
  }

  private void constractFilesInDependenciesPackagesMap(final PsiPackage pack,
                                                       final PsiPackage depPackage,
                                                       final PsiFile file,
                                                       final Map<PsiPackage, Map<PsiPackage, Set<PsiFile>>> filesInDependentPackages) {
    Map<PsiPackage, Set<PsiFile>> dependentPackages2Files = filesInDependentPackages.get(pack);
    if (dependentPackages2Files == null) {
      dependentPackages2Files = new HashMap<PsiPackage, Set<PsiFile>>();
      filesInDependentPackages.put(pack, dependentPackages2Files);
    }
    Set<PsiFile> depFiles = dependentPackages2Files.get(depPackage);
    if (depFiles == null) {
      depFiles = new HashSet<PsiFile>();
      dependentPackages2Files.put(depPackage, depFiles);
    }
    depFiles.add(file);
  }

//construct all dependencies for usage view
  private void constractWholeDependenciesMap(final PsiJavaFile psiJavaFile, final PsiFile psiFile) {
    Set<PsiFile> wholeDependencies = myForwardBuilder.getDependencies().get(psiJavaFile);
    if (wholeDependencies == null) {
      wholeDependencies = new HashSet<PsiFile>();
      myForwardBuilder.getDependencies().put(psiJavaFile, wholeDependencies);
    }
    wholeDependencies.add(psiFile);
  }

  public int getPerPackageCycleCount() {
    return myPerPackageCycleCount;
  }

  public Set<PsiFile> getDependentFilesInPackage(PsiPackage pack, PsiPackage depPack) {
    Set<PsiFile> psiFiles = new HashSet<PsiFile>();
    final Map<PsiPackage, Set<PsiFile>> map = myFilesInDependentPackages.get(pack);
    if (map != null){
      psiFiles = map.get(depPack);
    }
    if (psiFiles == null) {
      psiFiles = new HashSet<PsiFile>();
    }
    return psiFiles;
  }

  public Set<PsiFile> getDependentFilesInPackage(PsiPackage firstPack, PsiPackage middlePack, PsiPackage lastPack) {
    Set<PsiFile> result = new HashSet<PsiFile>();
    final Map<PsiPackage, Set<PsiFile>> forwardMap = myFilesInDependentPackages.get(middlePack);
    if (forwardMap != null && forwardMap.get(lastPack) != null){
      result.addAll(forwardMap.get(lastPack));
    }
    final Map<PsiPackage, Set<PsiFile>> backwardMap = myBackwardFilesInDependentPackages.get(middlePack);
    if (backwardMap != null && backwardMap.get(firstPack) != null){
      result.addAll(backwardMap.get(firstPack));
    }
    return result;
  }

  /*public Set<PsiFile> getDependentFilesInPackage(PsiPackage pack, PsiPackage depPack) {
    final Set<PsiFile> result = new HashSet<PsiFile>();
    final Map<PsiFile, Set<PsiFile>> dependencies = myForwardBuilder.getDependencies();
    final Set<PsiFile> allPsiPackageFiles = getAllPsiPackageFiles(pack);
    for (Iterator<PsiFile> it = allPsiPackageFiles.iterator(); it.hasNext();) {
      PsiFile file = it.next();
        final Set<PsiFile> psiFiles = dependencies.get(file);
        if (psiFiles == null){
          continue;
        }
        for (Iterator<PsiFile> iterator = psiFiles.iterator(); iterator.hasNext();) {
          PsiFile psiFile = iterator.next();
          if (psiFile.getContainingDirectory().getPackage().getQualifiedName().startsWith(depPack.getQualifiedName())){
            result.add(psiFile);
          }
      }
    }
    return result;
  }

  public Set<PsiFile> getDependentFilesInPackage(PsiPackage firstPack, PsiPackage middlePack, PsiPackage lastPack) {
    Set<PsiFile> result = new HashSet<PsiFile>();

    final Map<PsiFile, Set<PsiFile>> dependencies = myForwardBuilder.getDependencies();
    final Set<PsiFile> allPsiPackageFiles = getAllPsiPackageFiles(middlePack);

    for (Iterator<PsiFile> it = allPsiPackageFiles.iterator(); it.hasNext();) {
      PsiFile file = it.next();
      final Set<PsiFile> psiFiles = dependencies.get(file);
      if (psiFiles == null){
        continue;
      }
      for (Iterator<PsiFile> iterator = psiFiles.iterator(); iterator.hasNext();) {
        PsiFile psiFile = iterator.next();
        if (psiFile.getContainingDirectory().getPackage().getQualifiedName().startsWith(lastPack.getQualifiedName())){
          result.add(file);
        }
      }
    }
    result.addAll(getDependentFilesInPackage(firstPack, middlePack));
    return result;
  }

  private Set<PsiFile> getAllPsiPackageFiles(PsiPackage aPackage){
    Set<PsiFile> result = new HashSet<PsiFile>();
    final PsiDirectory[] directories = aPackage.getDirectories();
    if (directories == null){
      return result;
    }
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      final PsiFile[] files = directory.getFiles();
      if (files == null){
        continue;
      }
      for (int j = 0; j < files.length; j++) {
        PsiFile file = files[j];
        if (getScope().contains(file)){
          result.add(file);
        }
      }
      final PsiDirectory[] subdirectories = directory.getSubdirectories();
      if (subdirectories == null){
        continue;
      }
      for (int j = 0; j < subdirectories.length; j++) {
        PsiDirectory subdirectory = subdirectories[j];
        result.addAll(getAllPsiPackageFiles(subdirectory.getPackage()));
      }
    }
    return result;
  }
*/
  public HashMap<PsiPackage, Set<ArrayList<PsiPackage>>> getCyclicDependencies() {
    return myCyclicDependencies;
  }

  public HashMap<PsiPackage, Set<ArrayList<PsiPackage>>> getCycles(Collection<PsiPackage> packages, int perPackageCycleCount) {
    final HashMap<PsiPackage, Set<ArrayList<PsiPackage>>> result = new HashMap<PsiPackage, Set<ArrayList<PsiPackage>>>();
    final List<Chunk<PsiPackage>> chunks = buildChunks();
    for (Iterator<PsiPackage> iterator = packages.iterator(); iterator.hasNext();) {
      PsiPackage psiPackage = iterator.next();
      final List<Chunk<PsiPackage>> chunksByPackage = findChunksByPackage(psiPackage, chunks);
      for (Iterator<Chunk<PsiPackage>> it = chunksByPackage.iterator(); it.hasNext();) {
        Chunk<PsiPackage> chunk = it.next();
        if (chunk.getNodes().size() == 1){
          continue;
        }
        Set<ArrayList<PsiPackage>> paths2Pack = result.get(psiPackage);
        if (paths2Pack == null) {
          paths2Pack = new HashSet<ArrayList<PsiPackage>>();
          result.put(psiPackage, paths2Pack);
        }
        final GraphTraverser graphTraverser = new GraphTraverser(psiPackage, chunk, perPackageCycleCount);
        paths2Pack.addAll(graphTraverser.convert(graphTraverser.traverse()));
      }
    }
    /*for (Iterator<Chunk<PsiPackage>> iterator = chunks.iterator(); iterator.hasNext();) {
      Chunk<PsiPackage> chunk = iterator.next();
      for (Iterator<PsiPackage> it = chunk.getNodes().iterator(); it.hasNext();) {
        PsiPackage pack = it.next();
        Set<Chunk<PsiPackage>> chunks2Pack = result.get(pack);
        if (chunks2Pack == null) {
          chunks2Pack = new HashSet<Chunk<PsiPackage>>();
          result.put(pack, chunks2Pack);
        }
        chunks2Pack.add(chunk);
      }

    }*/
    return result;
  }

  private List<Chunk<PsiPackage>> findChunksByPackage(PsiPackage pack, List<Chunk<PsiPackage>> chunks) {
    List<Chunk<PsiPackage>> result = new ArrayList<Chunk<PsiPackage>>();
    for (Iterator<Chunk<PsiPackage>> iterator = chunks.iterator(); iterator.hasNext();) {
      Chunk<PsiPackage> chunk = iterator.next();
      if (chunk.containsNode(pack)) {
        result.add(chunk);
      }
    }
    return result;
  }

  public Map<String, PsiPackage> getAllProjectPackages() {
    if (myPackages.isEmpty()) {
      final PsiManager psiManager = PsiManager.getInstance(getProject());
      getScope().accept(new PsiRecursiveElementVisitor() {
        public void visitReferenceExpression(PsiReferenceExpression expression) {
        }

        public void visitFile(PsiFile file) {
          if (file != null && file instanceof PsiJavaFile) {
            PsiJavaFile psiJavaFile = (PsiJavaFile)file;
            final PsiPackage aPackage = psiManager.findPackage(psiJavaFile.getPackageName());
            if (aPackage != null) {
              myPackages.put(aPackage.getQualifiedName(), aPackage);
            }
          }
        }
      });
    }
    return myPackages;
  }


  private Graph<PsiPackage> buildGraph() {
    final Graph<PsiPackage> graph = GraphGenerator.create(CachingSemiGraph.create(new GraphGenerator.SemiGraph<PsiPackage>() {
      public Collection<PsiPackage> getNodes() {
        return getAllProjectPackages().values();
      }

      public Iterator<PsiPackage> getIn(PsiPackage psiPack) {
        final Set<PsiPackage> psiPackages = myPackageDependencies.get(psiPack);
        if (psiPackages == null) {     //for packs without java classes
          return new HashSet<PsiPackage>().iterator();
        }
        return psiPackages.iterator();
      }
    }));
    return graph;
  }

  public Set<PsiPackage> getPackageHierarhy(String packageName) {
    final Set<PsiPackage> result = new HashSet<PsiPackage>();
    PsiPackage psiPackage = findPackage(packageName);
    if (psiPackage != null) {
      result.add(psiPackage);
    }
    else {
      return result;
    }
    while (psiPackage.getParentPackage() != null && psiPackage.getParentPackage().getQualifiedName().length() != 0) {
      final PsiPackage aPackage = findPackage(psiPackage.getParentPackage().getQualifiedName());
      if (aPackage == null) {
        break;
      }
      result.add(aPackage);
      psiPackage = psiPackage.getParentPackage();
    }
    return result;
  }

  private PsiPackage findPackage(String packName) {
    final PsiPackage psiPackage = getAllProjectPackages().get(packName);
    return psiPackage;
  }

  private List<Chunk<PsiPackage>> buildChunks() {
    if (myGraph == null) {
        myGraph = buildGraph();
    }
    final DFSTBuilder<PsiPackage> dfstBuilder = new DFSTBuilder<PsiPackage>(myGraph);
    dfstBuilder.buildDFST();
    final LinkedList<Pair<Integer, Integer>> sccs = dfstBuilder.getSCCs();
    List<Chunk<PsiPackage>> chunks = new ArrayList<Chunk<PsiPackage>>();
    for (Iterator<Pair<Integer, Integer>> i = sccs.iterator(); i.hasNext();) {
      Set<PsiPackage> packs = new HashSet<PsiPackage>();
      final Pair<Integer, Integer> p = i.next();
      final Integer biT = p.getFirst();
      final int binum = biT.intValue();

      for (int j = 0; j < p.getSecond().intValue(); j++) {
        packs.add(dfstBuilder.getNodeByTNumber(binum + j));
      }
      chunks.add(new Chunk<PsiPackage>(packs));
    }
    return chunks;
  }

  private class GraphTraverser {
    private List<Path<PsiPackage>> myCurrentPaths = new ArrayList<Path<PsiPackage>>();
    private PsiPackage myBegin;
    private Chunk<PsiPackage> myChunk;
    private int myMaxPathsCount;

    public GraphTraverser(final PsiPackage begin, final Chunk<PsiPackage> chunk, int maxPathsCount) {
      myBegin = begin;
      myChunk = chunk;
      myMaxPathsCount = maxPathsCount;
    }

    public Set<Path<PsiPackage>> traverse() {
      Set<Path<PsiPackage>> result = new HashSet<Path<PsiPackage>>();
      Path<PsiPackage> firstPath = new Path<PsiPackage>();
      firstPath.add(myBegin);
      myCurrentPaths.add(firstPath);
      while (!myCurrentPaths.isEmpty() && result.size() < myMaxPathsCount) {
        final Path<PsiPackage> path = myCurrentPaths.get(0);
        final Set<PsiPackage> nextNodes = getNextNodes(path.getEnd());
        nextStep(nextNodes, path, result);
      }
      return result;
    }

    public Set<ArrayList<PsiPackage>> convert(Set<Path<PsiPackage>> paths) {
      Set<ArrayList<PsiPackage>> result = new HashSet<ArrayList<PsiPackage>>();
      for (Iterator<Path<PsiPackage>> iterator = paths.iterator(); iterator.hasNext();) {
        Path<PsiPackage> path = iterator.next();
        result.add(path.getPath());
      }
      return result;
    }

    private void nextStep(final Set<PsiPackage> nextNodes, final Path<PsiPackage> path, Set<Path<PsiPackage>> result) {
      myCurrentPaths.remove(path);
      for (Iterator<PsiPackage> iterator = nextNodes.iterator(); iterator.hasNext();) {
        PsiPackage node = iterator.next();
        if (path.getEnd() == node) {
          continue;
        }
        if (path.getBeg() == node) {
          result.add(path);
          continue;
        }
        Path<PsiPackage> newPath = new Path<PsiPackage>(path);
        newPath.add(node);
        if (path.contains(node)) {
          final Set<PsiPackage> nodesAfterInnerCycle = getNextNodes(node);
          nodesAfterInnerCycle.removeAll(path.getNextNodes(node));
          nextStep(nodesAfterInnerCycle, newPath, result);
        }
        else {
          myCurrentPaths.add(newPath);
        }
      }
    }

    private Set<PsiPackage> getNextNodes(PsiPackage node) {
      Set<PsiPackage> result = new HashSet<PsiPackage>();
      final Iterator<PsiPackage> in = myGraph.getIn(node);
      for (; in.hasNext();) {
        final PsiPackage psiPackage = in.next();
        if (myChunk.containsNode(psiPackage)) {
          result.add(psiPackage);
        }
      }
      return result;
    }
  }

  private static class Path <Node> {
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
}
