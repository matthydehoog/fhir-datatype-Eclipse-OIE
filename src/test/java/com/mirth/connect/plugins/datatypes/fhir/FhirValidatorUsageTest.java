package com.mirth.connect.plugins.datatypes.fhir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirSerializationProperties.FhirVersion;

public class FhirValidatorUsageTest {

    @BeforeClass
    public static void directEngine() {
        System.setProperty("fhir.engine.direct", "true");
    }

    @Test
    public void aSerializerKeepsItsOptionsInUseUntilItIsGone() throws Exception {
        FhirSerializationProperties p = new FhirSerializationProperties();
        p.setFhirVersion(FhirVersion.R4B);
        p.setTerminologyServer("http://usage-test.invalid/fhir"); // options no other test uses
        Map<String, String> options = p.engineOptions();

        FhirSerializer serializer = new FhirSerializer(new SerializerProperties(p, null, null));
        assertTrue(FhirValidatorUsage.inUse().contains(options));

        serializer = null; // as when the channel is undeployed
        for (int i = 0; i < 50 && FhirValidatorUsage.inUse().contains(options); i++) {
            System.gc();
            Thread.sleep(100);
        }
        assertFalse(FhirValidatorUsage.inUse().contains(options));
    }

    @Test
    public void aSerializerWithoutValidationDoesNotHoldAValidator() {
        FhirSerializationProperties p = new FhirSerializationProperties();
        p.setValidateInbound(false);
        p.setTerminologyServer("http://no-validation.invalid/fhir");
        new FhirSerializer(new SerializerProperties(p, null, null));
        assertFalse(FhirValidatorUsage.inUse().contains(p.engineOptions()));
    }

    @Test
    public void releaseHandsTheOptionsInUseToTheEngine() {
        FhirSerializationProperties p = new FhirSerializationProperties();
        p.setTerminologyServer("http://release-test.invalid/fhir");
        Object owner = new Object();
        FhirValidatorUsage.register(owner, p.engineOptions());

        List<Object[]> calls = new ArrayList<>();
        FhirValidatorUsage.releaseNow(new RecordingEngine(calls), 5);
        assertEquals(1, calls.size());
        assertTrue(((List<?>) calls.get(0)[0]).contains(p.engineOptions()));
        assertEquals(5L, calls.get(0)[1]);
        owner.hashCode(); // keeps the owner reachable until here
    }

    private static final class RecordingEngine implements FhirEngine {
        private final List<Object[]> calls;

        RecordingEngine(List<Object[]> calls) {
            this.calls = calls;
        }

        @Override
        public void retainValidators(List<Map<String, String>> inUse, long idleMillis) {
            calls.add(new Object[] { inUse, idleMillis });
        }

        // @formatter:off
        @Override public List<Map<String, String>> validate(String resource, Map<String, String> options) { throw new UnsupportedOperationException(); }
        @Override public String toJson(String xml, String fhirVersion, boolean pretty) { throw new UnsupportedOperationException(); }
        @Override public String toXml(String json, String fhirVersion, boolean pretty) { throw new UnsupportedOperationException(); }
        @Override public String reorderXml(String xml, String fhirVersion, boolean pretty) { throw new UnsupportedOperationException(); }
        @Override public void warmUp(Map<String, String> options) {}
        // @formatter:on
    }
}
