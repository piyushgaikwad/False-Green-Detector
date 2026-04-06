package com.fgd.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Minimal, security-hardened JUnit XML parser.
 *
 * Supports both {@code <testsuite>} (single-suite) and {@code <testsuites>}
 * (multi-suite) root elements. XXE and doctype expansion are disabled.
 */
public final class JUnitXmlParser {

    private JUnitXmlParser() {}

    public static ParsedReport parse(Path xmlPath) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setExpandEntityReferences(false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

        DocumentBuilder db = dbf.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(xmlPath)) {
            Document doc = db.parse(in);
            Element root = doc.getDocumentElement();
            if (root == null) throw new IllegalArgumentException("Empty XML document");

            String tag = root.getTagName();
            if ("testsuite".equalsIgnoreCase(tag)) {
                return fromSuiteElement(root);
            }
            if ("testsuites".equalsIgnoreCase(tag)) {
                return aggregateFromSuites(root);
            }
            throw new IllegalArgumentException("Unsupported root element: " + tag);
        }
    }

    private static ParsedReport fromSuiteElement(Element el) {
        return new ParsedReport(
                longAttr(el, "tests"),
                longAttr(el, "failures"),
                longAttr(el, "errors"),
                longAttr(el, "skipped")
        );
    }

    private static ParsedReport aggregateFromSuites(Element root) {
        long tests = longAttr(root, "tests");
        if (tests > 0) return fromSuiteElement(root);

        // Root has no tests attr — aggregate from child <testsuite> elements.
        NodeList suites = root.getElementsByTagName("testsuite");
        long t = 0, f = 0, e = 0, s = 0;
        for (int i = 0; i < suites.getLength(); i++) {
            if (suites.item(i) instanceof Element suite) {
                t += longAttr(suite, "tests");
                f += longAttr(suite, "failures");
                e += longAttr(suite, "errors");
                s += longAttr(suite, "skipped");
            }
        }
        return new ParsedReport(t, f, e, s);
    }

    private static long longAttr(Element el, String attr) {
        String v = el.getAttribute(attr);
        if (v == null || v.isBlank()) return 0;
        try { return Long.parseLong(v.trim()); }
        catch (NumberFormatException ignored) { return 0; }
    }

    public record ParsedReport(long totalTests, long failures, long errors, long skipped) {}
}
