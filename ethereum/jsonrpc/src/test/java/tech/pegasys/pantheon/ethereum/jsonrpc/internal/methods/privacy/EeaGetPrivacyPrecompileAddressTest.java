/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods.privacy;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import tech.pegasys.pantheon.ethereum.core.PrivacyParameters;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponseType;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;

import org.junit.Test;

public class EeaGetPrivacyPrecompileAddressTest {

  private final Integer privacyAddress = 127;
  private final PrivacyParameters privacyParameters = mock(PrivacyParameters.class);

  @Test
  public void verifyPrivacyPrecompileAddress() {
    when(privacyParameters.getPrivacyAddress()).thenReturn(privacyAddress);
    when(privacyParameters.isEnabled()).thenReturn(true);

    final EeaGetPrivacyPrecompileAddress eeaGetPrivacyPrecompileAddress =
        new EeaGetPrivacyPrecompileAddress(privacyParameters);

    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eea_getPrivacyPrecompileAddress", new Object[0]);

    final JsonRpcSuccessResponse response =
        (JsonRpcSuccessResponse) eeaGetPrivacyPrecompileAddress.response(request);

    assertEquals(privacyAddress, response.getResult());
  }

  @Test
  public void verifyErrorPrivacyDisabled() {
    when(privacyParameters.getPrivacyAddress()).thenReturn(privacyAddress);
    when(privacyParameters.isEnabled()).thenReturn(false);

    final EeaGetPrivacyPrecompileAddress eeaGetPrivacyPrecompileAddress =
        new EeaGetPrivacyPrecompileAddress(privacyParameters);

    final JsonRpcRequest request =
        new JsonRpcRequest("1", "eea_getPrivacyPrecompileAddress", new Object[0]);

    final JsonRpcResponse response = eeaGetPrivacyPrecompileAddress.response(request);

    assertEquals(JsonRpcResponseType.ERROR, response.getType());
    assertEquals(JsonRpcError.PRIVACY_NOT_ENABLED, ((JsonRpcErrorResponse) response).getError());
  }
}
