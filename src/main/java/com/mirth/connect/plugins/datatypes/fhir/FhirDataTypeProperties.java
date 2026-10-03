/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import java.util.HashMap;
import java.util.Map;

import com.mirth.connect.donkey.util.DonkeyElement;
import com.mirth.connect.model.datatype.BatchProperties;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializerProperties;

/** Serialization properties, and batch properties to split a Bundle into one message per entry. */
public class FhirDataTypeProperties extends DataTypeProperties {

    public FhirDataTypeProperties() {
        serializationProperties = new FhirSerializationProperties();
        batchProperties = new FhirBatchProperties();
    }

    /** Channels saved before 1.3.0 have no batch properties: XStream does not run the constructor. */
    @Override
    public BatchProperties getBatchProperties() {
        if (batchProperties == null) {
            batchProperties = new FhirBatchProperties();
        }
        return batchProperties;
    }

    @Override
    public SerializerProperties getSerializerProperties() {
        getBatchProperties();
        return super.getSerializerProperties();
    }

    // @formatter:off
    @Override public void migrate3_0_1(DonkeyElement element) {}
    @Override public void migrate3_0_2(DonkeyElement element) {}
    @Override public void migrate3_1_0(DonkeyElement element) {}
    @Override public void migrate3_2_0(DonkeyElement element) {}
    @Override public void migrate3_3_0(DonkeyElement element) {}
    @Override public void migrate3_4_0(DonkeyElement element) {}
    @Override public void migrate3_5_0(DonkeyElement element) {}
    @Override public void migrate3_6_0(DonkeyElement element) {}
    @Override public void migrate3_7_0(DonkeyElement element) {}
    @Override public void migrate3_9_0(DonkeyElement element) {}
    @Override public void migrate3_11_0(DonkeyElement element) {}
    @Override public void migrate3_11_1(DonkeyElement element) {}
    @Override public void migrate3_12_0(DonkeyElement element) {}
    // @formatter:on

    @Override
    public Map<String, Object> getPurgedProperties() {
        Map<String, Object> purged = new HashMap<String, Object>();
        purged.put("serializationProperties", serializationProperties.getPurgedProperties());
        purged.put("batchProperties", getBatchProperties().getPurgedProperties());
        return purged;
    }
}
