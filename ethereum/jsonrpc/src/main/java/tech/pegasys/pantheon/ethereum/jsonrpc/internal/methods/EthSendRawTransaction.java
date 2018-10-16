package tech.pegasys.pantheon.ethereum.jsonrpc.internal.methods;

import static tech.pegasys.pantheon.ethereum.jsonrpc.JsonRpcErrorConverter.convertTransactionInvalidReason;

import tech.pegasys.pantheon.ethereum.core.Transaction;
import tech.pegasys.pantheon.ethereum.core.TransactionPool;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.JsonRpcRequest;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.exception.InvalidJsonRpcRequestException;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.parameters.JsonRpcParameter;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcError;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcErrorResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcResponse;
import tech.pegasys.pantheon.ethereum.jsonrpc.internal.response.JsonRpcSuccessResponse;
import tech.pegasys.pantheon.ethereum.mainnet.TransactionValidator.TransactionInvalidReason;
import tech.pegasys.pantheon.ethereum.mainnet.ValidationResult;
import tech.pegasys.pantheon.ethereum.rlp.RLP;
import tech.pegasys.pantheon.ethereum.rlp.RLPException;
import tech.pegasys.pantheon.util.bytes.BytesValue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EthSendRawTransaction implements JsonRpcMethod {

  private static final Logger LOG = LogManager.getLogger();

  private final TransactionPool transactionPool;
  private final JsonRpcParameter parameters;

  public EthSendRawTransaction(
      final TransactionPool transactionPool, final JsonRpcParameter parameters) {
    this.transactionPool = transactionPool;
    this.parameters = parameters;
  }

  @Override
  public String getName() {
    return "eth_sendRawTransaction";
  }

  @Override
  public JsonRpcResponse response(final JsonRpcRequest request) {
    if (request.getParamLength() != 1) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }
    final String rawTransaction = parameters.required(request.getParams(), 0, String.class);

    Transaction transaction;
    try {
      transaction = decodeRawTransaction(rawTransaction);
    } catch (final InvalidJsonRpcRequestException e) {
      return new JsonRpcErrorResponse(request.getId(), JsonRpcError.INVALID_PARAMS);
    }

    final ValidationResult<TransactionInvalidReason> validationResult =
        transactionPool.addLocalTransaction(transaction);
    return validationResult.either(
        () -> new JsonRpcSuccessResponse(request.getId(), transaction.hash().toString()),
        errorReason ->
            new JsonRpcErrorResponse(
                request.getId(), convertTransactionInvalidReason(errorReason)));
  }

  private Transaction decodeRawTransaction(final String hash)
      throws InvalidJsonRpcRequestException {
    try {
      return Transaction.readFrom(RLP.input(BytesValue.fromHexString(hash)));
    } catch (IllegalArgumentException | RLPException e) {
      LOG.debug(e);
      throw new InvalidJsonRpcRequestException("Invalid raw transaction hex", e);
    }
  }
}