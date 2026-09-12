package io.github.vikthorvergara.playground.depadvisor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the direct dependencies of a single POM. No parent or BOM resolution:
 * anything without an explicit version ends up in the skipped list.
 */
final class PomParser {

    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");

    private PomParser() {
    }

    static DependencyList parse(PomFile pom) {
        Document doc = read(pom.xml());
        Element project = doc.getDocumentElement();
        Map<String, String> properties = properties(project);

        List<Dependency> dependencies = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (Element dependency : dependencyElements(project)) {
            String groupId = resolve(text(dependency, "groupId"), properties);
            String artifactId = resolve(text(dependency, "artifactId"), properties);
            String version = resolve(text(dependency, "version"), properties);
            if (groupId == null || artifactId == null) {
                continue;
            }
            if (version == null || version.contains("${")) {
                skipped.add(groupId + ":" + artifactId);
            } else {
                dependencies.add(new Dependency(groupId, artifactId, version));
            }
        }
        String name = text(project, "artifactId");
        return new DependencyList(name == null ? pom.source() : name, List.copyOf(dependencies), List.copyOf(skipped));
    }

    private static Document read(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Not a valid pom.xml: " + e.getMessage(), e);
        }
    }

    private static Map<String, String> properties(Element project) {
        Map<String, String> properties = new HashMap<>();
        String projectVersion = text(project, "version");
        if (projectVersion != null) {
            properties.put("project.version", projectVersion);
        }
        Element props = child(project, "properties");
        if (props != null) {
            NodeList nodes = props.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (nodes.item(i) instanceof Element e) {
                    properties.put(e.getTagName(), e.getTextContent().strip());
                }
            }
        }
        return properties;
    }

    /** Direct dependencies plus dependencyManagement entries, but not plugin dependencies. */
    private static List<Element> dependencyElements(Element project) {
        List<Element> result = new ArrayList<>();
        addDependencies(child(project, "dependencies"), result);
        Element management = child(project, "dependencyManagement");
        if (management != null) {
            addDependencies(child(management, "dependencies"), result);
        }
        return result;
    }

    private static void addDependencies(Element dependencies, List<Element> result) {
        if (dependencies == null) {
            return;
        }
        NodeList nodes = dependencies.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals("dependency")) {
                result.add(e);
            }
        }
    }

    private static Element child(Element parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(name)) {
                return e;
            }
        }
        return null;
    }

    private static String text(Element parent, String name) {
        Element e = child(parent, name);
        return e == null ? null : e.getTextContent().strip();
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value == null) {
            return null;
        }
        Matcher m = PROPERTY.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = properties.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement == null ? m.group() : replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
