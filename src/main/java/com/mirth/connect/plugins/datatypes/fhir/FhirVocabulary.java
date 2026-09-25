/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import com.mirth.connect.model.util.MessageVocabulary;

/** FHIR element names describe themselves (Patient, name, family), so no extra descriptions. */
public class FhirVocabulary extends MessageVocabulary {

    public FhirVocabulary(String version, String type) {
        super(version, type);
    }

    @Override
    public String getDescription(String elementId) {
        return "";
    }

    @Override
    public String getDataType() {
        return FhirDataTypeDelegate.NAME;
    }
}
