package com.mirth.connect.plugins.datatypes.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

/** The conversion that also runs in the Administrator: no HAPI involved. */
public class FhirXmlTest {

    @Test
    public void convertsJsonFollowingTheFhirRules() {
        String json = "{\"resourceType\":\"Patient\",\"id\":\"p1\","
                + "\"extension\":[{\"url\":\"http://example.org/x\",\"valueString\":\"a & b\"}],"
                + "\"name\":[{\"id\":\"n1\",\"family\":\"Jansen\",\"given\":[\"Piet\",\"Jan\"]}],"
                + "\"birthDate\":\"1980-01-01\",\"_birthDate\":{\"extension\":[{\"url\":\"http://example.org/t\",\"valueTime\":\"10:00:00\"}]},"
                + "\"multipleBirthInteger\":2,\"active\":true}";
        String xml = FhirXml.jsonToXml(json, true);
        assertTrue(xml, xml.startsWith("<Patient xmlns=\"http://hl7.org/fhir\">\n  <id value=\"p1\"/>\n"));
        assertTrue(xml, xml.contains("<extension url=\"http://example.org/x\">\n    <valueString value=\"a &amp; b\"/>\n  </extension>"));
        assertTrue(xml, xml.contains("<name id=\"n1\">\n    <family value=\"Jansen\"/>\n    <given value=\"Piet\"/>\n    <given value=\"Jan\"/>\n  </name>"));
        assertTrue(xml, xml.contains("<birthDate value=\"1980-01-01\">\n    <extension url=\"http://example.org/t\">\n      <valueTime value=\"10:00:00\"/>"));
        assertTrue(xml, xml.contains("<multipleBirthInteger value=\"2\"/>"));
        assertTrue(xml, xml.contains("<active value=\"true\"/>"));
    }

    @Test
    public void keepsDecimalsAndNewlinesExactly() {
        String xml = FhirXml.jsonToXml("{\"resourceType\":\"Observation\",\"valueQuantity\":{\"value\":1.50},\"note\":[{\"text\":\"line 1\\nline 2\"}]}", false);
        assertTrue(xml, xml.contains("<value value=\"1.50\"/>"));
        assertTrue(xml, xml.contains("<text value=\"line 1&#xA;line 2\"/>"));
    }

    @Test
    public void alignsPrimitiveArraysWithTheirExtensions() {
        String json = "{\"resourceType\":\"Patient\",\"name\":[{\"given\":[\"A\",null],\"_given\":[null,{\"id\":\"g2\",\"extension\":[{\"url\":\"u\",\"valueCode\":\"x\"}]}]}]}";
        String xml = FhirXml.jsonToXml(json, false);
        assertTrue(xml, xml.contains("<given value=\"A\"/>\n    <given id=\"g2\">\n      <extension url=\"u\">"));
    }

    @Test
    public void nestsContainedResourcesAndNarrative() {
        String json = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[{\"fullUrl\":\"urn:uuid:1\",\"resource\":{\"resourceType\":\"Patient\",\"id\":\"p\","
                + "\"text\":{\"status\":\"generated\",\"div\":\"<div xmlns=\\\"http://www.w3.org/1999/xhtml\\\"><p>Hi</p></div>\"}}}]}";
        String xml = FhirXml.jsonToXml(json, true);
        assertTrue(xml, xml.contains("<resource>\n      <Patient>\n        <id value=\"p\"/>"));
        assertTrue(xml, xml.contains("<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>Hi</p></div>"));
    }

    @Test
    public void stripsAndRestoresTheFhirNamespaceButNotXhtml() {
        String xml = "<?xml version=\"1.0\"?><Patient xmlns=\"http://hl7.org/fhir\"><text><div xmlns=\"http://www.w3.org/1999/xhtml\">x</div></text></Patient>";
        String stripped = FhirXml.stripNamespace(xml);
        assertEquals("<?xml version=\"1.0\"?><Patient><text><div xmlns=\"http://www.w3.org/1999/xhtml\">x</div></text></Patient>", stripped);
        assertEquals(xml, FhirXml.addNamespace(stripped));
        assertEquals(xml, FhirXml.addNamespace(xml));
    }

    @Test
    public void givesDivsTheXhtmlNamespaceBack() {
        assertEquals("<Patient xmlns=\"http://hl7.org/fhir\"><text><div xmlns=\"http://www.w3.org/1999/xhtml\">x</div></text></Patient>",
                FhirXml.addNamespace("<Patient><text><div>x</div></text></Patient>"));
    }

    @Test
    public void detectsTheFormatAndResourceType() {
        assertEquals("Patient", FhirXml.resourceType(" {\"resourceType\" : \"Patient\"}"));
        assertEquals("Bundle", FhirXml.resourceType("<?xml version=\"1.0\"?>\n<!-- c --><Bundle xmlns=\"http://hl7.org/fhir\"/>"));
        try {
            FhirXml.detect("MSH|^~\\&|");
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("neither FHIR JSON"));
        }
    }

    @Test
    public void rejectsJsonThatIsNotAResource() {
        try {
            FhirXml.jsonToXml("{\"name\":\"x\"}", true);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("resourceType"));
        }
        try {
            FhirXml.jsonToXml("{\"resourceType\":\"Patient\",\"id\":\"a\",\"id\":\"b\"}", true);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("duplicate"));
        }
        assertFalse(FhirXml.jsonToXml("{\"resourceType\":\"Patient\"}", false).contains("xmlns"));
    }
}
