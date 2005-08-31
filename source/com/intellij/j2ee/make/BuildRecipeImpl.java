/**
 * @author cdr
 */
package com.intellij.j2ee.make;

import com.intellij.openapi.module.Module;
import com.intellij.util.Degenerator;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.List;

public class BuildRecipeImpl implements BuildRecipe {
  private final List<BuildInstruction> myInstructions = new ArrayList<BuildInstruction>();

  public void addInstruction(BuildInstruction instruction) {
    if (!contains(instruction)) {
      myInstructions.add(instruction);
    }
  }

  public boolean contains(final BuildInstruction instruction) {
    return myInstructions.contains(instruction);
  }

  public boolean visitInstructions(BuildInstructionVisitor visitor, boolean reverseOrder){
    try {
      return visitInstructionsWithExceptions(visitor, reverseOrder);
    }
    catch (Exception e) {
      if (e instanceof RuntimeException) {
        throw (RuntimeException)e;
      }
      Degenerator.unableToDegenerateMarker();
      return false;
    }
  }
  public boolean visitInstructionsWithExceptions(BuildInstructionVisitor visitor, boolean reverseOrder) throws Exception {
    for (int i = reverseOrder ? myInstructions.size()-1 : 0;
         reverseOrder ? i>=0 : i < myInstructions.size();
         i += reverseOrder ? -1 : 1) {
      BuildInstruction instruction = myInstructions.get(i);
      if (!instruction.accept(visitor)) {
        return false;
      }
    }
    return true;
  }

  public void addAll(BuildRecipe buildRecipe) {
    buildRecipe.visitInstructions(new BuildInstructionVisitor() {
      public boolean visitInstruction(BuildInstruction instruction) throws RuntimeException {
        addInstruction(instruction);
        return true;
      }
    }, false);
  }

  public void addFileCopyInstruction(File file,
                                     boolean isDirectory, Module module,
                                     String outputRelativePath,
                                     FileFilter fileFilter) {
    if (fileFilter == null || fileFilter.accept(file)) {
      addInstruction(new FileCopyInstructionImpl(file, isDirectory, module, MakeUtil.trimForwardSlashes(outputRelativePath),fileFilter));
    }
  }

  public String toString() {
    String s = "Build recipe:";
    for (BuildInstruction buildInstruction : myInstructions) {
      s += "\n" + buildInstruction + "; ";
    }
    return s;
  }
}