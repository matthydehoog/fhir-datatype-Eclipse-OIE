package com.mirth.connect.plugins.datatypes.fhir;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

public class FhirToolsTest {

    private static final String PATIENT_JSON = "{\"resourceType\":\"Patient\",\"id\":\"p1\",\"gender\":\"male\",\"birthDate\":\"1980-01-01\"}";

    /** As the transformer sees it with Strip Namespaces on: msg.toString(). */
    private static final String PATIENT_XML_WITHOUT_NAMESPACE = "<Patient><id value=\"p1\"/><gender value=\"male\"/><birthDate value=\"1980-01-01\"/></Patient>";

    private static final String INVALID_XML_WITHOUT_NAMESPACE = "<Patient><gender value=\"x\"/></Patient>";

    @BeforeClass
    public static void directEngine() {
        System.setProperty("fhir.engine.direct", "true");
    }

    @Test
    public void validatesJson() throws Exception {
        assertTrue(FhirTools.validate(PATIENT_JSON).isValid());
    }

    @Test
    public void validatesTransformerXmlWithoutTheFhirNamespace() throws Exception {
        FhirValidation result = FhirTools.validate(PATIENT_XML_WITHOUT_NAMESPACE);
        assertTrue(result.summary(), result.isValid());
    }

    @Test
    public void reportsErrorsInTransformerXmlWithoutTheFhirNamespace() throws Exception {
        FhirValidation result = FhirTools.validate(INVALID_XML_WITHOUT_NAMESPACE);
        assertFalse(result.isValid());
        assertTrue(result.summary(), result.summary().contains("Patient.gender"));
    }

    @Test
    public void convertsTransformerXmlToJsonAndBack() throws Exception {
        String json = FhirTools.toJson(PATIENT_XML_WITHOUT_NAMESPACE);
        assertTrue(json, json.contains("\"resourceType\": \"Patient\"") || json.contains("\"resourceType\":\"Patient\""));
        assertTrue(FhirTools.toXml(json).contains("xmlns=\"http://hl7.org/fhir\""));
    }
}
