/*
 * User: anna
 * Date: 19-Feb-2008
 */
package com.intellij.openapi.projectRoots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkType;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.SystemProperties;
import org.jdom.Element;

public class JavaAwareProjectJdkTableImpl extends ProjectJdkTableImpl {
  public static JavaAwareProjectJdkTableImpl getInstanceEx() {
    return (JavaAwareProjectJdkTableImpl)ApplicationManager.getApplication().getComponent(ProjectJdkTable.class);
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
  public void readExternal(final Element element) throws InvalidDataException {
    myInternalJdk = null;
    try {
      super.readExternal(element);
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