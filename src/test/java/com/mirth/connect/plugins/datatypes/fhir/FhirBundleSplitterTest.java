package com.mirth.connect.plugins.datatypes.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.junit.Test;

/** Batch splitting of Bundles: no HAPI involved. */
public class FhirBundleSplitterTest {

    private static final String JSON_BUNDLE = "{\"resourceType\":\"Bundle\",\"id\":\"b1\",\"type\":\"batch\",\"entry\":["
            + "{\"fullUrl\":\"urn:uuid:1\",\"resource\":{\"resourceType\":\"Observation\",\"status\":\"final\",\"valueQuantity\":{\"value\":1.50}},\"request\":{\"method\":\"POST\",\"url\":\"Observation\"}},"
            + "{\"request\":{\"method\":\"DELETE\",\"url\":\"Observation/9\"}},"
            + "{\"fullUrl\":\"urn:uuid:2\",\"resource\":{\"resourceType\":\"Observation\",\"status\":\"final\",\"note\":[{\"text\":\"a \\\"b\\\"\\nc\"}]}},"
            + "{\"resource\":{\"resourceType\":\"Patient\",\"active\":true,\"name\":[]}}]}";

    private static final String XML_BUNDLE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Bundle xmlns=\"http://hl7.org/fhir\">\n"
            + "  <type value=\"collection\"/>\n"
            + "  <entry>\n"
            + "    <fullUrl value=\"urn:uuid:1\"/>\n"
            + "    <resource>\n"
            + "      <Patient>\n"
            + "        <text>\n"
            + "          <status value=\"generated\"/>\n"
            + "          <div xmlns=\"http://www.w3.org/1999/xhtml\">Piet &amp; Jan</div>\n"
            + "        </text>\n"
            + "        <active value=\"true\"/>\n"
            + "      </Patient>\n"
            + "    </resource>\n"
            + "    <request>\n"
            + "      <method value=\"PUT\"/>\n"
            + "      <url value=\"Patient/1\"/>\n"
            + "    </request>\n"
            + "  </entry>\n"
            + "  <entry>\n"
            + "    <resource>\n"
            + "      <Observation>\n"
            + "        <status value=\"final\"/>\n"
            + "      </Observation>\n"
            + "    </resource>\n"
            + "  </entry>\n"
            + "</Bundle>\n";

    @Test
    public void splitsJsonIntoOneMessagePerResource() {
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split(JSON_BUNDLE);
        assertEquals(3, parts.size());

        String first = parts.get(0).getMessage();
        assertTrue(first, first.startsWith("{\n  \"resourceType\": \"Observation\",\n  \"status\": \"final\","));
        assertTrue("decimal kept as written: " + first, first.contains("\"value\": 1.50"));
        assertEquals("Observation", FhirXml.resourceType(first));

        Map<String, Object> map = parts.get(0).getSourceMap();
        assertEquals("batch", map.get(FhirBundleSplitter.BUNDLE_TYPE));
        assertEquals("b1", map.get(FhirBundleSplitter.BUNDLE_ID));
        assertEquals(0, map.get(FhirBundleSplitter.ENTRY_INDEX));
        assertEquals(4, map.get(FhirBundleSplitter.ENTRY_COUNT));
        assertEquals("urn:uuid:1", map.get(FhirBundleSplitter.ENTRY_FULL_URL));
        assertEquals("POST", map.get(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
        assertEquals("Observation", map.get(FhirBundleSplitter.ENTRY_REQUEST_URL));

        // the DELETE entry (index 1) has no resource and gives no message
        assertEquals(2, parts.get(1).getSourceMap().get(FhirBundleSplitter.ENTRY_INDEX));
        assertFalse(parts.get(1).getSourceMap().containsKey(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
        assertTrue(parts.get(1).getMessage(), parts.get(1).getMessage().contains("\"text\": \"a \\\"b\\\"\\nc\""));

        assertTrue(parts.get(2).getMessage(), parts.get(2).getMessage().contains("\"active\": true,\n  \"name\": []"));
        assertFalse(parts.get(2).getSourceMap().containsKey(FhirBundleSplitter.ENTRY_FULL_URL));
    }

    @Test
    public void writtenJsonReadsBackTheSame() {
        for (FhirBundleSplitter.Part part : FhirBundleSplitter.split(JSON_BUNDLE)) {
            String xml = FhirXml.jsonToXml(part.getMessage(), true);
            assertEquals(xml, FhirXml.jsonToXml(FhirJson.write(FhirJson.parse(part.getMessage())), true));
        }
    }

    @Test
    public void splitsXmlKeepingNamespacesAndNarrative() {
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split(XML_BUNDLE);
        assertEquals(2, parts.size());

        String patient = parts.get(0).getMessage();
        assertTrue(patient, patient.startsWith("<Patient xmlns=\"http://hl7.org/fhir\">\n  <text>\n    <status value=\"generated\"/>"));
        assertTrue(patient, patient.contains("<div xmlns=\"http://www.w3.org/1999/xhtml\">Piet &amp; Jan</div>"));
        assertTrue(patient, patient.endsWith("</Patient>"));

        Map<String, Object> map = parts.get(0).getSourceMap();
        assertEquals("collection", map.get(FhirBundleSplitter.BUNDLE_TYPE));
        assertFalse(map.containsKey(FhirBundleSplitter.BUNDLE_ID));
        assertEquals(2, map.get(FhirBundleSplitter.ENTRY_COUNT));
        assertEquals("urn:uuid:1", map.get(FhirBundleSplitter.ENTRY_FULL_URL));
        assertEquals("PUT", map.get(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
        assertEquals("Patient/1", map.get(FhirBundleSplitter.ENTRY_REQUEST_URL));

        assertEquals("<Observation xmlns=\"http://hl7.org/fhir\">\n  <status value=\"final\"/>\n</Observation>", parts.get(1).getMessage());
        assertEquals(1, parts.get(1).getSourceMap().get(FhirBundleSplitter.ENTRY_INDEX));
    }

    @Test
    public void splitsXmlWithoutNamespace() {
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split(XML_BUNDLE.replace(" xmlns=\"http://hl7.org/fhir\"", ""));
        assertEquals(2, parts.size());
        assertTrue(parts.get(0).getMessage(), parts.get(0).getMessage().startsWith("<Patient xmlns=\"http://hl7.org/fhir\">"));
    }

    @Test
    public void splitsTheExampleBundle() throws Exception {
        String bundle = new String(Files.readAllBytes(Paths.get("examples/observation-bundle.json")), StandardCharsets.UTF_8);
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split(bundle);
        assertEquals(2, parts.size());
        assertEquals("Patient", FhirXml.resourceType(parts.get(0).getMessage()));
        assertEquals("Observation", FhirXml.resourceType(parts.get(1).getMessage()));
        assertEquals("transaction", parts.get(1).getSourceMap().get(FhirBundleSplitter.BUNDLE_TYPE));
    }

    @Test
    public void leavesOtherMessagesWhole() {
        String patient = "{\"resourceType\":\"Patient\",\"active\":true}";
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split("  " + patient + "\n");
        assertEquals(1, parts.size());
        assertEquals(patient, parts.get(0).getMessage());
        assertTrue(parts.get(0).getSourceMap().isEmpty());

        // unreadable: one message, so the data type's validation reports it
        assertEquals("{\"resourceType\":\"Bundle\",", FhirBundleSplitter.split("{\"resourceType\":\"Bundle\",").get(0).getMessage());
        assertEquals("<Bundle><entry>", FhirBundleSplitter.split("<Bundle><entry>").get(0).getMessage());
        assertEquals("not fhir", FhirBundleSplitter.split("not fhir").get(0).getMessage());
    }

    @Test
    public void anEmptyBundleGivesNoMessages() {
        assertTrue(FhirBundleSplitter.split("{\"resourceType\":\"Bundle\",\"type\":\"batch\"}").isEmpty());
        assertTrue(FhirBundleSplitter.split("<Bundle xmlns=\"http://hl7.org/fhir\"><type value=\"batch\"/></Bundle>").isEmpty());
        assertTrue(FhirBundleSplitter.split("  ").isEmpty());
    }

    @Test
    public void refusesDoctypes() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE Bundle [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                + "<Bundle xmlns=\"http://hl7.org/fhir\"><entry><resource><Patient><id value=\"&x;\"/></Patient></resource></entry></Bundle>";
        List<FhirBundleSplitter.Part> parts = FhirBundleSplitter.split(xxe);
        assertEquals(1, parts.size());
        assertTrue(parts.get(0).getMessage().startsWith("<?xml"));
    }
}
