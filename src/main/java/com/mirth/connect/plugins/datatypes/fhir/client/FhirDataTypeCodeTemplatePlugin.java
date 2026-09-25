/*
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * https://mozilla.org/MPL/2.0/.
 */

package com.mirth.connect.plugins.datatypes.fhir.client;

import com.mirth.connect.model.datatype.DataTypeDelegate;
import com.mirth.connect.plugins.DataTypeCodeTemplatePlugin;
import com.mirth.connect.plugins.datatypes.fhir.FhirDataTypeDelegate;

public class FhirDataTypeCodeTemplatePlugin extends DataTypeCodeTemplatePlugin {

    public FhirDataTypeCodeTemplatePlugin(String name) {
        super(name);
    }

    @Override
    protected DataTypeDelegate getDataTypeDelegate() {
        return new FhirDataTypeDelegate();
    }

    @Override
    protected String getDisplayName() {
        return "FHIR";
    }
}
