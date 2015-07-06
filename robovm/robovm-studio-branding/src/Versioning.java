/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Called in build-robovm-studio.sh to apply the version found in
 * robovm/robovm-idea/pom.xml to
 * robovm/robovm-studio-branding/idea/IdeaApplicationInfo.xml
 */
public class Versioning {
    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException,
            TransformerException {
        if (args.length != 3) {
            System.out.println("Usage: Versioning <robovm-idea-pom-xml> <robovm-studio-application-xml> <dmg-json>");
            System.exit(-1);
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document pomXml = builder.parse(new File(args[0]));

        NodeList elements = pomXml.getElementsByTagName("version");
        Node versionNode = elements.item(0);
        String version = versionNode.getTextContent();

        boolean isSnapshot = version.contains("-SNAPSHOT");
        if (isSnapshot)
            version = version.replace("-SNAPSHOT", "");
        String[] tokens = version.split("\\.");
        int major = Integer.parseInt(tokens[0]);
        int minor = Integer.parseInt(tokens[1]);
        int patch = Integer.parseInt(tokens[2]);

        Document versionXml = builder.parse(new File(args[1]));
        versionNode = versionXml.getElementsByTagName("version").item(0);
        versionNode.getAttributes().getNamedItem("major").setTextContent("" + major);
        versionNode.getAttributes().getNamedItem("minor").setTextContent("" + minor);
        versionNode.getAttributes().getNamedItem("patch").setTextContent("" + patch);
        versionNode.getAttributes().getNamedItem("full").setTextContent(version + (isSnapshot ? " EAP" : ""));
        versionNode.getAttributes().getNamedItem("eap").setTextContent(isSnapshot ? "true" : "false");

        Node buildNode = versionXml.getElementsByTagName("build").item(0);
        buildNode.getAttributes().getNamedItem("number").setTextContent("RS-" + major + "." + minor + "." + patch);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StreamResult result = new StreamResult(new StringWriter());
        DOMSource source = new DOMSource(versionXml);
        transformer.transform(source, result);
        String xmlString = result.getWriter().toString();
        FileWriter writer = new FileWriter(new File(args[1]));
        writer.write(xmlString);
        writer.close();

        System.out.println(version + (isSnapshot ? "-SNAPSHOT" : ""));
    }
}
