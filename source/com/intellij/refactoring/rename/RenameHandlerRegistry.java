package com.intellij.refactoring.rename;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.util.containers.HashSet;

import java.util.Iterator;
import java.util.Set;

/**
 * @author dsl
 */
public class RenameHandlerRegistry {
  private Set<RenameHandler> myHandlers  = new HashSet<RenameHandler>();
  private static final RenameHandlerRegistry INSTANCE = new RenameHandlerRegistry();
  private PsiElementRenameHandler myDefaultElementRenameHandler;

  public static RenameHandlerRegistry getInstance() {
    return INSTANCE;
  }

  private RenameHandlerRegistry() {
    myDefaultElementRenameHandler = new PsiElementRenameHandler();
  }

  public PsiElementRenameHandler getDefaultElementRenameHandler() {
    return myDefaultElementRenameHandler;
  }

  public RenameHandler getRenameHandler(DataContext dataContext) {
    for (Iterator<RenameHandler> iterator = myHandlers.iterator(); iterator.hasNext();) {
      RenameHandler renameHandler = iterator.next();
      if (renameHandler.isAvailableOnDataContext(dataContext)) return renameHandler;
    }
    return myDefaultElementRenameHandler.isAvailableOnDataContext(dataContext) ? myDefaultElementRenameHandler : null;
  }

  public void registerHandler(RenameHandler handler) {
    myHandlers.add(handler);
  }
}
