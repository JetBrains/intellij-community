/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.portable;

import com.intellij.util.Consumer;
import org.apache.subversion.javahl.callback.InfoCallback;
import org.apache.subversion.javahl.types.Info;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/21/12
 * Time: 7:18 PM
 */
public class InfoCallbackConvertor {
  public static InfoCallback create(final ISVNInfoHandler callback, final Consumer<SVNException> exceptionConsumer) {
    return new InfoCallback() {
      @Override
      public void singleInfo(Info info) {
        if (callback == null) return;
        try {
          callback.handleInfo(InfoConvertor.convert(info));
        }
        catch (SVNException e) {
          exceptionConsumer.consume(e);
        }
      }
    };
  }
}
