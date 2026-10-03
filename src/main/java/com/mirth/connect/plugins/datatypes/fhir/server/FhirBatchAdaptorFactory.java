/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir.server;

import com.mirth.connect.donkey.model.message.BatchRawMessage;
import com.mirth.connect.donkey.server.channel.SourceConnector;
import com.mirth.connect.donkey.server.message.batch.BatchAdaptor;
import com.mirth.connect.model.datatype.SerializerProperties;
import com.mirth.connect.plugins.datatypes.fhir.FhirBatchProperties;
import com.mirth.connect.server.message.DebuggableBatchAdaptorFactory;

public class FhirBatchAdaptorFactory extends DebuggableBatchAdaptorFactory {
    public FhirBatchAdaptorFactory(SourceConnector sourceConnector, SerializerProperties serializerProperties) {
        super(sourceConnector, serializerProperties);
        if (batchProperties == null) {
            batchProperties = new FhirBatchProperties();
        }
    }

    @Override
    public BatchAdaptor createBatchAdaptor(BatchRawMessage batchRawMessage) {
        FhirBatchAdaptor batchAdaptor = new FhirBatchAdaptor(this, sourceConnector, batchRawMessage);

        batchAdaptor.setBatchProperties(batchProperties);

        return batchAdaptor;
    }
}
