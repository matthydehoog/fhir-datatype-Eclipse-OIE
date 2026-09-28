package com.mirth.connect.plugins.datatypes.fhir;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;

/** The terminology server option, against the public server tx.fhir.org. Skipped without network. */
public class TerminologyServerTest {

    private static final String SERVER = "https://tx.fhir.org/r4";

    @BeforeClass
    public static void directEngine() {
        System.setProperty("fhir.engine.direct", "true");
    }

    private static boolean reachable() {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(SERVER + "/metadata?_summary=true").toURL().openConnection();
            c.setConnectTimeout(5000);
            c.setReadTimeout(15000);
            c.setRequestProperty("Accept", "application/fhir+json");
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private static String observation(String loinc) {
        return "{\"resourceType\":\"Observation\",\"status\":\"final\","
                + "\"code\":{\"coding\":[{\"system\":\"http://loinc.org\",\"code\":\"" + loinc + "\"}]},"
                + "\"valueQuantity\":{\"value\":5.4,\"unit\":\"mmol/L\",\"system\":\"http://unitsofmeasure.org\",\"code\":\"mmol/L\"}}";
    }

    private static String issues(List<Map<String, String>> issues) {
        StringBuilder s = new StringBuilder();
        for (Map<String, String> i : issues) {
            s.append(i.get(FhirEngine.SEVERITY)).append(' ').append(i.get(FhirEngine.LOCATION)).append(": ").append(i.get(FhirEngine.MESSAGE)).append('\n');
        }
        return s.toString();
    }

    @Test
    public void checksCodesOnTheTerminologyServer() throws Exception {
        assumeTrue("no connection to " + SERVER, reachable());
        Map<String, String> options = new HashMap<>();
        options.put(FhirEngine.FHIR_VERSION, "R4");
        options.put(FhirEngine.TERMINOLOGY_SERVER, SERVER);
        FhirEngine engine = FhirEngineLoader.engine();

        // The validator cannot check LOINC itself: without a server both codes only get the warning
        // "CodeSystem is unknown and can't be validated". With the server, glucose passes and an
        // unknown code is an error.
        String valid = issues(engine.validate(observation("2345-7"), options));
        assertTrue(valid, valid.isEmpty());

        String unknown = issues(engine.validate(observation("99999-99"), options));
        assertTrue(unknown, unknown.startsWith("error Observation.code") && unknown.contains("99999-99"));
        assertFalse(unknown, unknown.contains("can't be validated"));
    }
}
