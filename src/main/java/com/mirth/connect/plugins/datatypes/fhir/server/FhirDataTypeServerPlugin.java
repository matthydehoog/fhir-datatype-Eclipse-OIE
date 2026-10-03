/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir.server;

import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.AutoResponder;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptorFactory;
import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.model.datatype.ResponseGenerationProperties;
import com.mirth.connect.model.datatype.SerializationProperties;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirSerializationProperties;
import com.mirth.connect.plugins.DataTypeServerPlugin;
import com.mirth.connect.plugins.datatypes.fhir.FhirDataTypeDelegate;

public class FhirDataTypeServerPlugin extends DataTypeServerPlugin {
    private final DataTypeDelegate dataTypeDelegate = new FhirDataTypeDelegate();

    @Override
    public String getPluginPointName() {
        return dataTypeDelegate.getName();
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    /** Process Batch on the source connector: one message per Bundle entry, or a split script. */
    @Override
    public BatchAdaptorFactory getBatchAdaptorFactory(SourceConnector sourceConnector, SerializerProperties properties) {
        return new FhirBatchAdaptorFactory(sourceConnector, properties);
    }

    /** "Auto-generate" source responses: a FHIR OperationOutcome instead of an empty response. */
    @Override
    public AutoResponder getAutoResponder(SerializationProperties serializationProperties, ResponseGenerationProperties responseGenerationProperties) {
        return new FhirAutoResponder(serializationProperties instanceof FhirSerializationProperties ? (FhirSerializationProperties) serializationProperties : null);
    }

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return dataTypeDelegate;
    }
}
