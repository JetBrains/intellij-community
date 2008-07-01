package com.intellij.facet.impl.autodetecting.model;

/**
 * @author nik
 */
public interface DetectedFacetInfo<M> extends FacetInfo2<M> {

  int getId();

  String getDetectorId();
}
