/*
 * Created by IntelliJ IDEA.
 * User: dsl
 * Date: 09.07.2002
 * Time: 15:41:09
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.refactoring.util.classMembers;

import java.util.EventListener;

public interface MemberInfoChangeListener extends EventListener {
  void memberInfoChanged(MemberInfoChange event);
}
