/*
 * User: anna
 * Date: 19-Feb-2008
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.util.SystemProperties;
import org.jdom.Element;

@State(
  name="ProjectJdkTable",
  storages= {
    @Storage(
      id="other",
      file = "$APP_CONFIG$/jdk.table.xml"
    )}
)
public class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ServiceManager.getService(ProjectJdkTable.class);
  }

  private JavaSdk myJavaSdk;
  private Sdk myInternalJdk;

  public JavaAwareProjectJdkTableImpl(final JavaSdk javaSdk) {
    myJavaSdk = javaSdk;
  }

  public Sdk getInternalJdk() {
    if (myInternalJdk == null) {
      final String jdkHome = SystemProperties.getJavaHome();
      final String versionName = ProjectBundle.message("sdk.java.name.template", SystemProperties.getJavaVersion());
      myInternalJdk = myJavaSdk.createJdk(versionName, jdkHome);
    }
    return myInternalJdk;
  }

  @Override
  public void removeJdk(final Sdk jdk) {
    super.removeJdk(jdk);
    if (jdk.equals(myInternalJdk)) {
      myInternalJdk = null;
    }
  }

  @Override
  public SdkType getDefaultSdkType() {
    return myJavaSdk;
  }

  @Override
  public void loadState(final Element element) {
    myInternalJdk = null;
    try {
      super.loadState(element);
    }
    finally {
      getInternalJdk();
    }
  }

  @Override
  protected String getSdkTypeName(final String type) {
    return type != null ? type : JavaSdk.getInstance().getName();
  }
}