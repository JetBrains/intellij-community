
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
package org.jetbrains.idea.maven.services.nexus;

import org.jvnet.ws.wadl.util.DSDispatcher;
import org.jvnet.ws.wadl.util.JAXBDispatcher;
import org.jvnet.ws.wadl.util.UriBuilder;

import javax.activation.DataSource;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;

public class Endpoint {


    public static class DataIndex {

        private JAXBDispatcher _jaxbDispatcher;
        private DSDispatcher _dsDispatcher;
        private UriBuilder _uriBuilder;
        private JAXBContext _jc;
        private HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         *
         * @param nexusRoot
         */
        public DataIndex(final String nexusRoot)
            throws JAXBException
        {
            _jc = JAXBContext.newInstance("org.jetbrains.idea.maven.services.nexus", getClass().getClassLoader());
            _jaxbDispatcher = new JAXBDispatcher(_jc);
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(nexusRoot);
            _matrixParamSet = _uriBuilder.addPathSegment("data_index");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getArtifactlistAsApplicationXml()
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public SearchResults getArtifactlistAsSearchResults()
            throws IOException, MalformedURLException, JAXBException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            Object _retVal = _jaxbDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            if (_retVal == null) {
                return null;
            }
            if (JAXBElement.class.isInstance(_retVal)) {
                JAXBElement jaxbElement = ((JAXBElement) _retVal);
                _retVal = jaxbElement.getValue();
            }
            return ((SearchResults) _retVal);
        }

        public DataSource getArtifactlistAsApplicationXml(String q, String g, String a, String v, String c)
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            _queryParameterValues.put("q", q);
            _queryParameterValues.put("g", g);
            _queryParameterValues.put("a", a);
            _queryParameterValues.put("v", v);
            _queryParameterValues.put("c", c);
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public SearchResults getArtifactlistAsSearchResults(String q, String g, String a, String v, String c, String cn)
            throws IOException, MalformedURLException, JAXBException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            _queryParameterValues.put("q", q);
            _queryParameterValues.put("g", g);
            _queryParameterValues.put("a", a);
            _queryParameterValues.put("v", v);
            _queryParameterValues.put("c", c);
            _queryParameterValues.put("cn", cn);
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            Object _retVal = _jaxbDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            if (_retVal == null) {
                return null;
            }
            if (JAXBElement.class.isInstance(_retVal)) {
                JAXBElement jaxbElement = ((JAXBElement) _retVal);
                _retVal = jaxbElement.getValue();
            }
            return ((SearchResults) _retVal);
        }

    }

    public static class DataIndexRepository {

        private JAXBDispatcher _jaxbDispatcher;
        private DSDispatcher _dsDispatcher;
        private UriBuilder _uriBuilder;
        private JAXBContext _jc;
        private HashMap<String, Object> _templateAndMatrixParameterValues;

        /**
         * Create new instance
         * 
         */
        public DataIndexRepository(String repository)
            throws JAXBException
        {
            _jc = JAXBContext.newInstance("org.jetbrains.idea.maven.services.nexus", getClass().getClassLoader());
            _jaxbDispatcher = new JAXBDispatcher(_jc);
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment("http://repository.sonatype.org/service/local/");
            _matrixParamSet = _uriBuilder.addPathSegment("data_index/{repository}");
            _templateAndMatrixParameterValues = new HashMap<>();
            _templateAndMatrixParameterValues.put("repository", repository);
        }

        /**
         * Get repository
         * 
         */
        public String getRepository() {
            return ((String) _templateAndMatrixParameterValues.get("repository"));
        }

        /**
         * Set repository
         * 
         */
        public void setRepository(String repository) {
            _templateAndMatrixParameterValues.put("repository", repository);
        }

        public DataSource getArtifactlistAsApplicationXml()
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public SearchResults getArtifactlistAsSearchResults()
            throws IOException, MalformedURLException, JAXBException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            Object _retVal = _jaxbDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            if (_retVal == null) {
                return null;
            }
            if (JAXBElement.class.isInstance(_retVal)) {
                JAXBElement jaxbElement = ((JAXBElement) _retVal);
                _retVal = jaxbElement.getValue();
            }
            return ((SearchResults) _retVal);
        }

        public DataSource getArtifactlistAsApplicationXml(String q, String g, String a, String v, String c)
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            _queryParameterValues.put("q", q);
            _queryParameterValues.put("g", g);
            _queryParameterValues.put("a", a);
            _queryParameterValues.put("v", v);
            _queryParameterValues.put("c", c);
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public SearchResults getArtifactlistAsSearchResults(String q, String g, String a, String v, String c)
            throws IOException, MalformedURLException, JAXBException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            _queryParameterValues.put("q", q);
            _queryParameterValues.put("g", g);
            _queryParameterValues.put("a", a);
            _queryParameterValues.put("v", v);
            _queryParameterValues.put("c", c);
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            Object _retVal = _jaxbDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            if (_retVal == null) {
                return null;
            }
            if (JAXBElement.class.isInstance(_retVal)) {
                JAXBElement jaxbElement = ((JAXBElement) _retVal);
                _retVal = jaxbElement.getValue();
            }
            return ((SearchResults) _retVal);
        }

    }

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
            _jc = JAXBContext.newInstance("org.jetbrains.idea.maven.services.nexus", getClass().getClassLoader());
            _jaxbDispatcher = new JAXBDispatcher(_jc);
            _dsDispatcher = new DSDispatcher();
            _uriBuilder = new UriBuilder();
            List<String> _matrixParamSet;
            _matrixParamSet = _uriBuilder.addPathSegment(url);
            _matrixParamSet = _uriBuilder.addPathSegment("repositories");
            _templateAndMatrixParameterValues = new HashMap<>();
        }

        public DataSource getRepolistAsApplicationXml()
            throws IOException, MalformedURLException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            DataSource _retVal = _dsDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            return _retVal;
        }

        public org.jetbrains.idea.maven.services.nexus.Repositories getRepolistAsRepositories()
            throws IOException, MalformedURLException, JAXBException
        {
            HashMap<String, Object> _queryParameterValues = new HashMap<>();
            HashMap<String, Object> _headerParameterValues = new HashMap<>();
            String _url = _uriBuilder.buildUri(_templateAndMatrixParameterValues, _queryParameterValues);
            Object _retVal = _jaxbDispatcher.doGET(_url, _headerParameterValues, "application/xml");
            if (_retVal == null) {
                return null;
            }
            if (JAXBElement.class.isInstance(_retVal)) {
                JAXBElement jaxbElement = ((JAXBElement) _retVal);
                _retVal = jaxbElement.getValue();
            }
            return ((org.jetbrains.idea.maven.services.nexus.Repositories) _retVal);
        }

    }

}
