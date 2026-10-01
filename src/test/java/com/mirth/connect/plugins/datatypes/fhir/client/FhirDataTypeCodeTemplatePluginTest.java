package com.mirth.connect.plugins.datatypes.fhir.client;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.EvaluatorException;
import org.mozilla.javascript.Scriptable;

import com.mirth.connect.model.codetemplates.CodeTemplate;
import com.mirth.connect.model.codetemplates.ContextType;

public class FhirDataTypeCodeTemplatePluginTest {

    private static Map<String, List<CodeTemplate>> referenceItems() {
        return new FhirDataTypeCodeTemplatePlugin("FHIR").getReferenceItems();
    }

    private static CodeTemplate template(String name) {
        for (CodeTemplate t : referenceItems().get(FhirDataTypeCodeTemplatePlugin.CATEGORY)) {
            if (t.getName().equals(name)) {
                return t;
            }
        }
        throw new AssertionError("No template " + name);
    }

    @Test
    public void keepsTheConversionTemplatesAndAddsTheFhirCategory() {
        Map<String, List<CodeTemplate>> items = referenceItems();
        assertTrue(items.containsKey("Conversion Functions"));
        assertFalse(items.get("Conversion Functions").isEmpty());

        List<String> names = new ArrayList<String>();
        for (CodeTemplate t : items.get(FhirDataTypeCodeTemplatePlugin.CATEGORY)) {
            names.add(t.getName());
        }
        assertTrue(names.containsAll(List.of("Validate FHIR resource", "Convert FHIR XML to JSON", "Convert FHIR JSON to XML",
                "Is the FHIR message valid", "Get FHIR issue count", "Get FHIR issues", "Read a FHIR value", "Add a FHIR element",
                "Iterate FHIR Bundle entries")));
    }

    @Test
    public void everyTemplateHasCodeADescriptionAndAContext() {
        for (CodeTemplate t : referenceItems().get(FhirDataTypeCodeTemplatePlugin.CATEGORY)) {
            assertFalse(t.getName(), t.getCode().trim().isEmpty());
            assertFalse(t.getName(), t.getDescription().trim().isEmpty());
            assertFalse(t.getName(), t.getContextSet().isEmpty());
        }
    }

    @Test
    public void e4xTemplatesOnlyShowWhereMsgIsXml() {
        assertTrue(template("Read a FHIR value").getContextSet().contains(ContextType.SOURCE_FILTER_TRANSFORMER));
        assertFalse(template("Read a FHIR value").getContextSet().contains(ContextType.CHANNEL_DEPLOY));
        assertTrue(template("Convert FHIR JSON to XML").getContextSet().contains(ContextType.CHANNEL_DEPLOY));
    }

    /** Every snippet must be valid JavaScript (with E4X) for the engine's Rhino. */
    @Test
    public void everyTemplateCompilesInRhinoWithE4x() {
        Context cx = new ContextFactory().enterContext();
        try {
            cx.setLanguageVersion(Context.VERSION_DEFAULT);
            for (CodeTemplate t : referenceItems().get(FhirDataTypeCodeTemplatePlugin.CATEGORY)) {
                // Filter rules contain a return statement, so compile each snippet as a function body.
                String source = "function rule() {\n" + t.getCode() + "\n}";
                try {
                    cx.compileString(source, t.getName(), 1, null);
                } catch (EvaluatorException e) {
                    throw new AssertionError(t.getName() + ": " + e.getMessage());
                }
            }
        } finally {
            Context.exit();
        }
    }

    /** The E4X templates run against FHIR XML as the transformer sees it (namespace stripped). */
    @Test
    public void e4xTemplatesWorkOnStrippedFhirXml() {
        Context cx = new ContextFactory().enterContext();
        try {
            Scriptable scope = cx.initStandardObjects();
            cx.evaluateString(scope, "var msg = new XML('<Patient><id value=\"p1\"/><name><family value=\"Jansen\"/></name>"
                    + "<gender value=\"male\"/><extension url=\"http://example.org/fhir/StructureDefinition/my-extension\">"
                    + "<valueString value=\"x\"/></extension></Patient>');", "setup", 1, null);

            cx.evaluateString(scope, template("Read a FHIR value").getCode(), "read", 1, null);
            assertEquals("Jansen", Context.toString(scope.get("family", scope)));

            cx.evaluateString(scope, template("Set a FHIR value").getCode(), "set", 1, null);
            assertEquals("female", Context.toString(cx.evaluateString(scope, "msg.gender.@value.toString()", "check", 1, null)));

            cx.evaluateString(scope, template("Add a FHIR element").getCode(), "add", 1, null);
            assertEquals("1980-01-01", Context.toString(cx.evaluateString(scope, "msg.birthDate.@value.toString()", "check", 1, null)));

            cx.evaluateString(scope, template("Find a FHIR extension by URL").getCode(), "ext", 1, null);
            assertEquals("x", Context.toString(cx.evaluateString(scope, "extension.valueString.@value.toString()", "check", 1, null)));

            cx.evaluateString(scope, "var msg = new XML('<Observation><code><coding><system value=\"http://snomed.info/sct\"/><code value=\"1\"/></coding>"
                    + "<coding><system value=\"http://loinc.org\"/><code value=\"8302-2\"/></coding></code></Observation>');", "obs", 1, null);
            cx.evaluateString(scope, template("Find a FHIR coding by system").getCode(), "coding", 1, null);
            assertEquals("8302-2", Context.toString(scope.get("loinc", scope)));

            cx.evaluateString(scope, "var logged = []; var logger = { info: function(s) { logged.push(s); } };"
                    + "var msg = new XML('<Bundle><entry><resource><Patient><id value=\"a\"/></Patient></resource></entry>"
                    + "<entry><resource><Observation><id value=\"b\"/></Observation></resource></entry></Bundle>');", "bundle", 1, null);
            cx.evaluateString(scope, template("Iterate FHIR Bundle entries").getCode(), "iterate", 1, null);
            assertEquals("Patient/a,Observation/b", Context.toString(cx.evaluateString(scope, "logged.join(',')", "check", 1, null)));
        } finally {
            Context.exit();
        }
    }
}
