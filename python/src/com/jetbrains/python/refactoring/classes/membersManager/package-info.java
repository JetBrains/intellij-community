/**
 * Incapsulates knowledge about class members that could be moved to some other class.
 * To use (get list of members to move or actually move them) use {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager#getAllMembersCouldBeMoved(com.jetbrains.python.psi.PyClass)}
 * and                                                            {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager#moveAllMembers(java.util.Collection, com.jetbrains.python.psi.PyClass, com.jetbrains.python.psi.PyClass)}
 *
 * This class delegates its behaviour to its managers (some kind of plugins). There is one for each member type (one for method, one for field etc).
 * You need to extend {@link com.jetbrains.python.refactoring.classes.membersManager.MembersManager} to add some. See its javadoc for more info.
 *
 *
 * @author Ilya.Kazakevich
 */
package com.jetbrains.python.refactoring.classes.membersManager;