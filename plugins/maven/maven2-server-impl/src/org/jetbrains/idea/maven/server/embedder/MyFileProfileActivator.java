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
package org.jetbrains.idea.maven.server.embedder;

import org.apache.maven.model.Activation;
import org.apache.maven.model.ActivationFile;
import org.apache.maven.model.Profile;
import org.apache.maven.profiles.activation.DetectedProfileActivator;
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.StringUtils;

import java.io.File;
import java.io.IOException;

/**
 * Copied from org.apache.maven.profiles.activation.FileProfileActivator
 * added parameter baseDit to test file existing
 *
 * @author Sergey Evdokimov
 */
public class MyFileProfileActivator extends DetectedProfileActivator
    implements LogEnabled
{
    private Logger logger;

    private final File baseDir;

    public MyFileProfileActivator(File baseDir) {
      this.baseDir = baseDir;
    }

  protected boolean canDetectActivation( Profile profile )
    {
        return profile.getActivation() != null && profile.getActivation().getFile() != null;
    }

    public boolean isActive( Profile profile )
    {
        Activation activation = profile.getActivation();

        ActivationFile actFile = activation.getFile();

        if ( actFile != null )
        {
            // check if the file exists, if it does then the profile will be active
            String fileString = actFile.getExists();

            RegexBasedInterpolator interpolator = new RegexBasedInterpolator();
            try
            {
                interpolator.addValueSource( new EnvarBasedValueSource() );
            }
            catch ( IOException e )
            {
                // ignored
            }
            interpolator.addValueSource( new MapBasedValueSource( System.getProperties() ) );

            try
            {
                if ( StringUtils.isNotEmpty(fileString) )
                {
                    fileString = StringUtils.replace( interpolator.interpolate( fileString, "" ), "\\", "/" );
                    return fileExists(fileString);
                }

                // check if the file is missing, if it is then the profile will be active
                fileString = actFile.getMissing();

                if ( StringUtils.isNotEmpty( fileString ) )
                {
                    fileString = StringUtils.replace( interpolator.interpolate( fileString, "" ), "\\", "/" );
                    return !fileExists(fileString);
                }
            }
            catch ( InterpolationException e )
            {
                if ( logger.isDebugEnabled() )
                {
                    logger.debug( "Failed to interpolate missing file location for profile activator: " + fileString, e );
                }
                else
                {
                    logger.warn( "Failed to interpolate missing file location for profile activator: " + fileString + ". Run in debug mode (-X) for more information." );
                }
            }
        }

        return false;
    }

    private boolean fileExists(String path) {
      return new File(path).exists() || new File(baseDir, path).exists();
    }

    public void enableLogging( Logger logger )
    {
        this.logger = logger;
    }
}
