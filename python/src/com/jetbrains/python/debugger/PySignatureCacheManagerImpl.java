package com.jetbrains.python.debugger;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.django.util.VirtualFileUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author traff
 */
public class PySignatureCacheManagerImpl extends PySignatureCacheManager {
  protected static final Logger LOG = Logger.getInstance(PySignatureCacheManagerImpl.class.getName());

  public static final FileAttribute CALL_SIGNATURES_ATTRIBUTE = new FileAttribute("call.signatures.attribute", 1, true);

  private final Project myProject;

  public PySignatureCacheManagerImpl(Project project) {
    myProject = project;
  }

  @Override
  public void recordSignature(PySignature signature) {
    GlobalSearchScope scope = ProjectScopeBuilder.getInstance(myProject).buildProjectScope();

    VirtualFile file = getFile(signature);
    if (file != null && scope.contains(file)) {
      recordSignature(file, signature);
    }
  }

  private static void recordSignature(VirtualFile file, PySignature signature) {
    byte[] data;
    try {
      data = CALL_SIGNATURES_ATTRIBUTE.readAttributeBytes(file);
    }
    catch (Exception e) {
      data = null;
    }

    String[] lines;
    if (data != null) {
      lines = (new String(data)).split("\n");
    }
    else {
      lines = new String[0];
    }

    boolean found = false;
    int i = 0;
    for (String sign : lines) {
      String[] parts = sign.split("\t");
      if (parts.length > 0 && parts[0].equals(signature.getFunctionName())) {
        found = true;
        lines[i] = signatureToString(signature);
      }
      i++;
    }
    if (!found) {
      String[] lines2 = new String[lines.length + 1];
      for (int j = 0; j < lines.length; j++) {
        lines2[j] = lines[j];
      }
      lines2[lines2.length - 1] = signatureToString(signature);
      lines = lines2;
    }

    String attrString = StringUtils.join(lines, "\n");

    try {
      CALL_SIGNATURES_ATTRIBUTE.writeAttributeBytes(file, attrString.getBytes());
    }
    catch (IOException e) {
      LOG.warn("Can't write attribute " + file.getCanonicalPath() + " " + attrString);
    }
  }

  private static String signatureToString(PySignature signature) {
    return signature.getFunctionName() + "\t" + StringUtils.join(arguments(signature), '\t');
  }

  private static List<String> arguments(PySignature signature) {
    List<String> res = Lists.newArrayList();
    for (PySignature.NamedParameter param : signature.getArgs()) {
      res.add(param.getName() + ":" + param.getType());
    }
    return res;
  }

  @Nullable
  public static String findParameterType(@NotNull PyNamedParameter parameter) {
    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      PySignature signature = findSignature(function);
      if (signature != null) {
        return signature.getArgType(parameter.getName());
      }
    }
    return null;
  }

  @Nullable
  public static PySignature findSignature(@NotNull PyFunction function) {
    VirtualFile file = getFile(function);
    if (file != null) {
      return readSignatureAttributeFromFile(file, getFunctionName(function));
    }
    else {
      return null;
    }
  }

  private static String getFunctionName(PyFunction function) {
    String name = function.getName();
    if (name == null) {
      return "";
    }

    PyClass cls = function.getContainingClass();

    if (cls != null) {
      name = cls.getName() + "." + name;
    }

    return name;
  }

  @Nullable
  private static PySignature readSignatureAttributeFromFile(@NotNull VirtualFile file, @NotNull String name) {
    byte[] data;
    try {
      data = CALL_SIGNATURES_ATTRIBUTE.readAttributeBytes(file);
    }
    catch (IOException e) {
      data = null;
    }

    if (data != null) {
      String[] lines = (new String(data)).split("\n");
      for (String sign : lines) {
        String[] parts = sign.split("\t");
        if (parts.length > 0 && parts[0].equals(name)) {
          PySignature signature = new PySignature(file.getCanonicalPath(), name);
          for (int i = 1; i < parts.length; i++) {
            String[] var = parts[i].split(":");
            if (var.length == 2) {
              signature = signature.addArgumentVar(var[0], var[1]);
            }
            else {
              throw new IllegalStateException("Should be <name>:<type> format. " + parts[i] + " instead.");
            }
          }
          return signature;
        }
      }
    }

    return null;
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PySignature signature) {
    return VirtualFileUtil.findFile(signature.getFile());
  }

  @Nullable
  private static VirtualFile getFile(@NotNull PyFunction function) {
    PsiFile file = function.getContainingFile();

    return file != null ? file.getOriginalFile().getVirtualFile() : null;
  }
}
