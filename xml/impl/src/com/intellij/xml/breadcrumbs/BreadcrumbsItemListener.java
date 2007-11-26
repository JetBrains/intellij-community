package com.intellij.xml.breadcrumbs;

import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface BreadcrumbsItemListener<T extends BreadcrumbsItem> {

  void itemSelected(@NotNull final T item);

}
