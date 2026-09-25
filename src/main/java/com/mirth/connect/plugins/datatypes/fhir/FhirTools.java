/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import com.mirth.connect.plugins.datatypes.fhir.FhirSerializationProperties.FailOn;

/**
 * FHIR functions for channel scripts, for example to validate a response or build JSON in a
 * transformer:
 *
 * <pre>
 * var FhirTools = Packages.com.mirth.connect.plugins.datatypes.fhir.FhirTools;
 * var result = FhirTools.validate(responseJson);
 * if (!result.isValid()) logger.warn(result.summary());
 * </pre>
 *
 * Runs on the server only.
 */
public final class FhirTools {

    private FhirTools() {}

    /** Validates a FHIR JSON or XML resource against the base specification and its meta.profile. */
    public static FhirValidation validate(String resource) throws Exception {
        return validate(resource, "", "");
    }

    /** Validates against a profile, which must be known (see the next method for your own profiles). */
    public static FhirValidation validate(String resource, String requiredProfile) throws Exception {
        return validate(resource, requiredProfile, "");
    }

    /**
     * @param requiredProfile canonical URL of a profile the resource must conform to, or empty
     * @param profiles files and folders with profiles and packages, separated by ';', or empty
     */
    public static FhirValidation validate(String resource, String requiredProfile, String profiles) throws Exception {
        FhirSerializationProperties properties = new FhirSerializationProperties();
        properties.setRequiredProfile(requiredProfile == null ? "" : requiredProfile);
        properties.setProfiles(profiles == null ? "" : profiles);
        return new FhirValidation(FhirEngineLoader.engine().validate(resource, properties.engineOptions()), FailOn.Errors);
    }

    /** FHIR XML (with or without the FHIR namespace, e.g. msg.toString()) to FHIR JSON. */
    public static String toJson(String xml) throws Exception {
        return FhirEngineLoader.engine().toJson(FhirXml.addNamespace(xml.trim()), "R4", true);
    }

    /** FHIR JSON to FHIR XML, with the FHIR namespace. */
    public static String toXml(String json) throws Exception {
        return FhirEngineLoader.engine().toXml(json, "R4", true);
    }
}
