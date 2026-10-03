package com.mirth.connect.plugins.datatypes.fhir.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.message.batch.BatchMessageReader;
import com.mirth.connect.plugins.datatypes.fhir.FhirBatchProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirBundleSplitter;
import com.mirth.connect.plugins.datatypes.fhir.FhirDataTypeProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirXml;

/** Process Batch with "Split Batch By: Bundle Entry", the way the source connector drives it. */
public class FhirBatchAdaptorTest {

    private static final String BUNDLE = "{\"resourceType\":\"Bundle\",\"type\":\"batch\",\"entry\":["
            + "{\"fullUrl\":\"urn:uuid:a\",\"resource\":{\"resourceType\":\"Observation\",\"id\":\"o1\"},\"request\":{\"method\":\"POST\",\"url\":\"Observation\"}},"
            + "{\"fullUrl\":\"urn:uuid:b\",\"resource\":{\"resourceType\":\"Observation\",\"id\":\"o2\"},\"request\":{\"method\":\"PUT\",\"url\":\"Observation/o2\"}},"
            + "{\"fullUrl\":\"urn:uuid:c\",\"resource\":{\"resourceType\":\"Observation\",\"id\":\"o3\"}}]}";

    /** What SourceConnector.dispatchBatchMessage does: get a message, then copy the batch's source map. */
    private static List<Map<String, Object>> drain(FhirBatchAdaptor adaptor, BatchRawMessage raw, List<String> messages) throws Exception {
        List<Map<String, Object>> maps = new ArrayList<>();
        String message;
        while ((message = adaptor.getMessage()) != null) {
            messages.add(message);
            maps.add(new HashMap<>(raw.getSourceMap()));
        }
        return maps;
    }

    private static FhirBatchAdaptor adaptor(BatchRawMessage raw) {
        FhirBatchAdaptor adaptor = new FhirBatchAdaptor(null, null, raw);
        adaptor.setBatchProperties(new FhirBatchProperties());
        return adaptor;
    }

    @Test
    public void aBundleWithThreeEntriesGivesThreeMessages() throws Exception {
        Map<String, Object> original = new HashMap<>();
        original.put("originalFilename", "bundle.json");
        BatchRawMessage raw = new BatchRawMessage(new BatchMessageReader(BUNDLE), original);

        List<String> messages = new ArrayList<>();
        List<Map<String, Object>> maps = drain(adaptor(raw), raw, messages);

        assertEquals(3, messages.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("Observation", FhirXml.resourceType(messages.get(i)));
            assertTrue(messages.get(i), messages.get(i).contains("\"id\": \"o" + (i + 1) + "\""));
            assertEquals("bundle.json", maps.get(i).get("originalFilename"));
            assertEquals("batch", maps.get(i).get(FhirBundleSplitter.BUNDLE_TYPE));
            assertEquals(i, maps.get(i).get(FhirBundleSplitter.ENTRY_INDEX));
            assertEquals(3, maps.get(i).get(FhirBundleSplitter.ENTRY_COUNT));
            assertEquals("urn:uuid:" + (char) ('a' + i), maps.get(i).get(FhirBundleSplitter.ENTRY_FULL_URL));
        }
        assertEquals("POST", maps.get(0).get(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
        assertEquals("Observation/o2", maps.get(1).get(FhirBundleSplitter.ENTRY_REQUEST_URL));
        // no request on the third entry: the second entry's values must not leak into it
        assertNull(maps.get(2).get(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
        assertNull(maps.get(2).get(FhirBundleSplitter.ENTRY_REQUEST_URL));
    }

    @Test
    public void anXmlBundleIsSplitToo() throws Exception {
        String xml = FhirXml.jsonToXml(BUNDLE, true);
        BatchRawMessage raw = new BatchRawMessage(new BatchMessageReader(xml), new HashMap<String, Object>());
        List<String> messages = new ArrayList<>();
        List<Map<String, Object>> maps = drain(adaptor(raw), raw, messages);
        assertEquals(3, messages.size());
        assertTrue(messages.get(1), messages.get(1).startsWith("<Observation xmlns=\"http://hl7.org/fhir\">\n  <id value=\"o2\"/>"));
        assertEquals("PUT", maps.get(1).get(FhirBundleSplitter.ENTRY_REQUEST_METHOD));
    }

    @Test
    public void theExampleBundleGivesOneMessagePerEntry() throws Exception {
        String bundle = new String(Files.readAllBytes(Paths.get("examples/observation-bundle.json")), StandardCharsets.UTF_8);
        BatchRawMessage raw = new BatchRawMessage(new BatchMessageReader(bundle), new HashMap<String, Object>());
        List<String> messages = new ArrayList<>();
        drain(adaptor(raw), raw, messages);
        assertEquals(2, messages.size());
    }

    @Test
    public void channelsSavedBeforeBatchSupportGetDefaultBatchProperties() throws Exception {
        FhirDataTypeProperties properties = new FhirDataTypeProperties();
        java.lang.reflect.Field field = com.mirth.connect.model.datatype.DataTypeProperties.class.getDeclaredField("batchProperties");
        field.setAccessible(true);
        field.set(properties, null); // as XStream leaves it for a channel saved by 1.2.0
        assertTrue(properties.getBatchProperties() instanceof FhirBatchProperties);
        assertEquals(FhirBatchProperties.SplitType.Bundle_Entry, ((FhirBatchProperties) properties.getSerializerProperties().getBatchProperties()).getSplitType());
        assertEquals("Bundle Entry", FhirBatchProperties.SplitType.Bundle_Entry.toString());
    }
}
