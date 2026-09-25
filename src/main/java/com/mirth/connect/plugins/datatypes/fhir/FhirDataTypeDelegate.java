/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir;

import com.mirth.connect.donkey.model.message.SerializationType;
import com.mirth.connect.model.converters.IMessageSerializer;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.datatype.DataTypeProperties;
import com.mirth.connect.model.datatype.SerializerProperties;

public class FhirDataTypeDelegate implements DataTypeDelegate {
    public static final String NAME = "FHIR";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public IMessageSerializer getSerializer(SerializerProperties properties) {
        return new FhirSerializer(properties);
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    /** The transformer works on FHIR XML (E4X), like the other structured data types. */
    @Override
    public SerializationType getDefaultSerializationType() {
        return SerializationType.XML;
    }

    @Override
    public DataTypeProperties getDefaultProperties() {
        return new FhirDataTypeProperties();
    }
}
