package com.jetbrains.python.codeInsight;

import com.intellij.openapi.util.Key;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author yole
 */
public class PyStdlibClassMembersProvider extends PyClassMembersProviderBase {
  private Key<List<PyDynamicMember>> mySocketMembersKey = Key.create("socket.members");

  @Override
  public Collection<PyDynamicMember> getMembers(PyClassType classType) {
    PyClass clazz = classType.getPyClass();
    if (clazz == null) {
      return Collections.emptyList();
    }
    final String qualifiedName = clazz.getQualifiedName();
    if ("socket._socketobject".equals(qualifiedName)) {
      final PyFile socketFile = (PyFile)clazz.getContainingFile();
      List<PyDynamicMember> socketMembers = socketFile.getUserData(mySocketMembersKey);
      if (socketMembers == null) {
        socketMembers = calcSocketMembers(socketFile);
        socketFile.putUserData(mySocketMembersKey, socketMembers);
      }
      return socketMembers;
    }
    return Collections.emptyList();
  }

  private static List<PyDynamicMember> calcSocketMembers(PyFile socketFile) {
    List<PyDynamicMember> result = new ArrayList<PyDynamicMember>();
    addMethodsFromAttr(socketFile, result, "_socketmethods");
    addMethodsFromAttr(socketFile, result, "_delegate_methods");
    return result;
  }

  private static void addMethodsFromAttr(PyFile socketFile, List<PyDynamicMember> result, final String attrName) {
    final PyTargetExpression socketMethods = socketFile.findTopLevelAttribute(attrName);
    if (socketMethods != null) {
      final List<String> methods = PyUtil.getStringListFromTargetExpression(socketMethods);
      if (methods != null) {
        for (String name : methods) {
          result.add(new PyDynamicMember(name).resolvesTo("_socket").toClass("SocketType").toFunction(name));
        }
      }
    }
  }
}
