
/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.services.artifactory;

import org.jvnet.ws.wadl.util.DSDispatcher;
import org.jvnet.ws.wadl.util.JAXBDispatcher;
import org.jvnet.ws.wadl.util.UriBuilder;

import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


/**
 * 
 */
public class Endpoint {

  public static DataSource getArtifactInfoByUri(String uri)
    throws IOException, MalformedURLException {
    DSDispatcher _dsDispatcher = new DSDispatcher();
    UriBuilder _uriBuilder = new UriBuilder();
    _uriBuilder.addPathSegment(uri);
    String _url = _uriBuilder.buildUri(Collections.<String, Object>emptyMap(), Collections.<String, Object>emptyMap());
    DataSource _retVal =
      _dsDispatcher.doGET(_url, Collections.<String, Object>emptyMap(), "application/vnd.org.jfrog.artifactory.search.ArtifactSearchResult+json");
    return _retVal;
  }

  public static class Search {

        public static class Artifact {

            private DSDispatcher _dsDispatcher;
            private UriBuilder _uriBuilder;
            private JAXBContext _jc;
            private HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Artifact(final String url)
                throws JAXBException
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("artifact");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getArtifactSearchResultJson(String name, String repos)
                throws IOException, MalformedURLException
            {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("name", name);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.ArtifactSearchResult+json");
                return _retVal;
            }

        }

        public static class Gavc {

            private JAXBDispatcher _jaxbDispatcher;
            private DSDispatcher _dsDispatcher;
            private UriBuilder _uriBuilder;
            private JAXBContext _jc;
            private HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Gavc(final String url)
                throws JAXBException
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("gavc");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getGavcSearchResultJson(String g, String a, String v, String c, String repos)
                throws IOException, MalformedURLException
            {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("g", g);
                _queryParameterValues.put("a", a);
                _queryParameterValues.put("v", v);
                _queryParameterValues.put("c", c);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.GavcSearchResult+json");
                return _retVal;
            }

        }
        public static class Archive {

            private JAXBDispatcher _jaxbDispatcher;
            private DSDispatcher _dsDispatcher;
            private UriBuilder _uriBuilder;
            private JAXBContext _jc;
            private HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Archive(final String url)
                throws JAXBException
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("search");
                _matrixParamSet = _uriBuilder.addPathSegment("archive");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource getArchiveSearchResultJson(String className, String repos)
                throws IOException, MalformedURLException
            {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                _queryParameterValues.put("name", className);
                _queryParameterValues.put("repos", repos);
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.search.ArchiveEntrySearchResult+json");
                return _retVal;
            }

        }

    }

    public static class System {

        private JAXBDispatcher _jaxbDispatcher;
        private DSDispatcher _dsDispatcher;
        private UriBuilder _uriBuilder;
        private JAXBContext _jc;
        private HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         *
         * @param url
         */
        public System(final String url)
            throws JAXBException
        {
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(url);
            _matrixParamSet = _uriBuilder.addPathSegment("system");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getAsApplicationXml()
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public static class Configuration {

            private JAXBDispatcher _jaxbDispatcher;
            private DSDispatcher _dsDispatcher;
            private UriBuilder _uriBuilder;
            private JAXBContext _jc;
            private HashMap<String, Object> _templateAndMatrixParameterValues;

            /**
             * Create new instance
             *
             * @param url
             */
            public Configuration(final String url)
                throws JAXBException
            {
                _dsDispatcher = new DSDispatcher();
                _uriBuilder = new UriBuilder();
                List<String> _matrixParamSet;
                _matrixParamSet = _uriBuilder.addPathSegment(url);
                _matrixParamSet = _uriBuilder.addPathSegment("system");
                _matrixParamSet = _uriBuilder.addPathSegment("configuration");
                _templateAndMatrixParameterValues = new HashMap<>();
            }

            public DataSource postAsTextPlain(DataSource input)
                throws IOException, MalformedURLException
            {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doPOST(input, "application/xml", _url, _headerParameterValues, "text/plain");
                return _retVal;
            }

            public DataSource getAsApplicationXml()
                throws IOException, MalformedURLException
            {
                HashMap<String, Object> _queryParameterValues = new HashMap<>();
                HashMap<String, Object> _headerParameterValues = new HashMap<>();
                String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
                return _retVal;
            }

            public static class RemoteRepositories {

                private JAXBDispatcher _jaxbDispatcher;
                private DSDispatcher _dsDispatcher;
                private UriBuilder _uriBuilder;
                private JAXBContext _jc;
                private HashMap<String, Object> _templateAndMatrixParameterValues;

                /**
                 * Create new instance
                 *
                 * @param url
                 */
                public RemoteRepositories(final String url)
                    throws JAXBException
                {
                    _dsDispatcher = new DSDispatcher();
                    _uriBuilder = new UriBuilder();
                    List<String> _matrixParamSet;
                    _matrixParamSet = _uriBuilder.addPathSegment(url);
                    _matrixParamSet = _uriBuilder.addPathSegment("system");
                    _matrixParamSet = _uriBuilder.addPathSegment("configuration");
                    _matrixParamSet = _uriBuilder.addPathSegment("remoteRepositories");
                    _templateAndMatrixParameterValues = new HashMap<>();
                }

                public void put(DataSource input)
                    throws IOException, MalformedURLException
                {
                    HashMap<String, Object> _queryParameterValues = new HashMap<>();
                    HashMap<String, Object> _headerParameterValues = new HashMap<>();
                    String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
                    DataSource _retVal = _dsDispatcher.doPUT(input, "application/xml", _url, _headerParameterValues, null);
                    return ;
                }

            }

        }

    }

    public static class SystemVersion {

        private JAXBDispatcher _jaxbDispatcher;
        private DSDispatcher _dsDispatcher;
        private UriBuilder _uriBuilder;
        private JAXBContext _jc;
        private HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         *
         * @param url
         */
        public SystemVersion(final String url)
            throws JAXBException
        {
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(url);
            _matrixParamSet = _uriBuilder.addPathSegment("system/version");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getSystemVersionJson()
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.system.Version+json");
            return _retVal;
        }

    }

  /**
  * @author Gregory.Shrago
  */
  public static class Repositories {

      private JAXBDispatcher _jaxbDispatcher;
      private DSDispatcher _dsDispatcher;
      private UriBuilder _uriBuilder;
      private JAXBContext _jc;
      private HashMap<String, Object> _templateAndMatrixParameterValues;

      /**
       * Create new instance
       *
       * @param url
       */
      public Repositories(String url)
          throws JAXBException
      {
          _dsDispatcher = new DSDispatcher();
          _uriBuilder = new UriBuilder();
          List<String> _matrixParamSet;
          _matrixParamSet = _uriBuilder.addPathSegment(url);
          _matrixParamSet = _uriBuilder.addPathSegment("repositories");
          _templateAndMatrixParameterValues = new HashMap<>();
      }

      public DataSource getRepositoryDetailsListJson(String type)
          throws IOException, MalformedURLException
      {
          HashMap<String, Object> _queryParameterValues = new HashMap<>();
          HashMap<String, Object> _headerParameterValues = new HashMap<>();
          _queryParameterValues.put("type", type);
          String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
          DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.repositories.RepositoryDetailsList+json");
          return _retVal;
      }

      public static class RepoKeyConfiguration {

          private JAXBDispatcher _jaxbDispatcher;
          private DSDispatcher _dsDispatcher;
          private UriBuilder _uriBuilder;
          private JAXBContext _jc;
          private HashMap<String, Object> _templateAndMatrixParameterValues;

          /**
           * Create new instance
           *
           */
          public RepoKeyConfiguration(final String url, String repokey)
              throws JAXBException
          {
              _dsDispatcher = new DSDispatcher();
              _uriBuilder = new UriBuilder();
              List<String> _matrixParamSet;
              _matrixParamSet = _uriBuilder.addPathSegment(url);
              _matrixParamSet = _uriBuilder.addPathSegment("repositories");
              _matrixParamSet = _uriBuilder.addPathSegment("{repoKey}/configuration");
              _templateAndMatrixParameterValues = new HashMap<>();
              _templateAndMatrixParameterValues.put("repoKey", repokey);
          }

          /**
           * Get repoKey
           *
           */
          public String getRepoKey() {
              return ((String) _templateAndMatrixParameterValues.get("repoKey"));
          }

          /**
           * Set repoKey
           *
           */
          public void setRepoKey(String repokey) {
              _templateAndMatrixParameterValues.put("repoKey", repokey);
          }

          public DataSource getAsApplicationVndOrgJfrogArtifactoryRepositoriesRepositoryConfigurationJson()
              throws IOException, MalformedURLException
          {
              HashMap<String, Object> _queryParameterValues = new HashMap<>();
              HashMap<String, Object> _headerParameterValues = new HashMap<>();
              String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
              DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/vnd.org.jfrog.artifactory.repositories.RepositoryConfiguration+json");
              return _retVal;
          }

      }

  }
}
