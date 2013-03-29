package com.intellij.designer.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface IComponentDeletionParticipant {
  /**
   * Called when one or more children are about to be deleted by the user.
   *
   * @param parent  the parent of the deleted children (which still contains
   *                the children since this method is called before the deletion
   *                is performed)
   * @param deleted a nonempty list of children about to be deleted
   * @return true if the children have been fully deleted by this participant; false
   *         if normal deletion should resume. Note that even though an implementation may return
   *         false from this method, that does not mean it did not perform any work. For example,
   *         a RelativeLayout handler could remove constraints pointing to now deleted components,
   *         but leave the overall deletion of the elements to the core designer.
   */
  boolean deleteChildren(@NotNull RadComponent parent, @NotNull List<RadComponent> deleted) throws Exception;
}