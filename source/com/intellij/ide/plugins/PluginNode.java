package com.intellij.ide.plugins;

import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: Mar 27, 2003
 * Time: 10:07:29 PM
 * To change this template use Options | File Templates.
 */
public class PluginNode implements TreeNode {
  public static final int STATUS_UNKNOWN = 0;
  public static final int STATUS_OUT_OF_DATE = 1;
  public static final int STATUS_MISSING = 2;
  public static final int STATUS_CURRENT = 3;
  public static final int STATUS_NEWEST = 4;
  public static final int STATUS_DOWNLOADED = 5;
  public static final int STATUS_DELETED = 6;
  public static final int STATUS_CART = 7;

  public static final String [] STATUS_NAMES = {
    "Unknown",
    "Out of date",
    "Not installed",
    "Installed",
    "Newest",
    "Downloaded",     // downloaded, but not activated
    "Uninstalled",    // uninstalled, but not activated
    "Shopping Cart"   // added to the Shopping Cart
  };

  private CategoryNode parent;

  private String name;
  private String version;
  private String vendor;
  private String description;
  private String sinceBuild;
  private String changeNotes;
  private String downloads;
  private String size;
  private String vendorEmail;
  private String vendorUrl;
  private String url;
  private String date;
  private List<String> depends;

  private int status = STATUS_UNKNOWN;
  private boolean loaded = false;

  public PluginNode() {
  }

  public PluginNode(String name) {
    this.name = name;
  }

  public String getChangeNotes() { return changeNotes; }

  public void setChangeNotes(String changeNotes) { this.changeNotes = changeNotes; }

  /**
   * Returns the child <code>TreeNode</code> at index
   * <code>childIndex</code>.
   */
  public TreeNode getChildAt(int childIndex) {
    return null;
  }

  /**
   * Returns the number of children <code>TreeNode</code>s the receiver
   * contains.
   */
  public int getChildCount() {
    return 0;
  }

  /**
   * Returns the parent <code>TreeNode</code> of the receiver.
   */
  public CategoryNode getParent() {
    return parent;
  }

  /**
   * Returns the index of <code>node</code> in the receivers children.
   * If the receiver does not contain <code>node</code>, -1 will be
   * returned.
   */
  public int getIndex(TreeNode node) {
    return 0;
  }

  /**
   * Returns true if the receiver allows children.
   */
  public boolean getAllowsChildren() {
    return false;
  }

  /**
   * Returns true if the receiver is a leaf.
   */
  public boolean isLeaf() {
    return true;
  }

  /**
   * @return Returns the children of the receiver as an <code>Enumeration</code>.
   */
  public Enumeration<TreeNode> children() {
    return null;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  /**
   * Be carefull when comparing Plugins versions. Use
   * PluginManagerColumnInfo.compareVersion() for version comparing.
   *
   * @return Return plugin version
   */
  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getVendor() {
    return vendor;
  }

  public void setVendor(String vendor) {
    this.vendor = vendor;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getSinceBuild() {
    return sinceBuild;
  }

  public void setSinceBuild(String sinceBuild) {
    this.sinceBuild = sinceBuild;
  }

  public void setParent(CategoryNode parent) {
    this.parent = parent;
  }

  /**
   * In complex environment use PluginManagerColumnInfo.getRealNodeState () method instead.
   * @return Status of plugin
   */
  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public String getStatusName () {
    return STATUS_NAMES [status];
  }

  public static String getStatusName (int status) {
    return STATUS_NAMES [status];
  }

  public String toString() {
    return getName();
  }

  public boolean isLoaded() {
    return loaded;
  }

  public void setLoaded(boolean loaded) {
    this.loaded = loaded;
  }

  public String getDownloads() { return downloads; }

  public void setDownloads(String downloads) { this.downloads = downloads; }

  public String getSize() { return size; }

  public void setSize(String size) { this.size = size; }

  public String getVendorEmail() {
    return vendorEmail;
  }

  public void setVendorEmail(String vendorEmail) {
    this.vendorEmail = vendorEmail;
  }

  public String getVendorUrl() {
    return vendorUrl;
  }

  public void setVendorUrl(String vendorUrl) {
    this.vendorUrl = vendorUrl;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getDate() {
    return date;
  }

  public void setDate(String date) {
    this.date = date;
  }

  public TreePath getPath() {
    List nodes = new ArrayList ();
    nodes.add(this);
    CategoryNode start = (CategoryNode)getParent();

    while (start != null) {
      nodes.add(0, start);
      start = (CategoryNode)start.getParent();
    }

    TreePath path = new TreePath(nodes.toArray());
    return path;
  }

  public int hashCode() {
    return name.hashCode();
  }

  public boolean equals(Object object) {
    if (object instanceof PluginNode) {
      return name.equals(((PluginNode)object).getName());
    } else
      return false;
  }

  public List<String> getDepends() {
    return depends;
  }

  public void setDepends(List<String> depends) {
    this.depends = depends;
  }

  public void addDepends (String depends) {
    if (this.depends == null)
      this.depends = new ArrayList<String> ();

    this.depends.add(depends);
  }
}
