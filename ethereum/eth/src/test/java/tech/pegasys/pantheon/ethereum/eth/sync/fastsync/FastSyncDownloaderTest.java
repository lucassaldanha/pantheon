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
package tech.pegasys.pantheon.ethereum.eth.sync.fastsync;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.CHAIN_TOO_SHORT;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.NO_PEERS_AVAILABLE;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncError.UNEXPECTED_ERROR;
import static tech.pegasys.pantheon.ethereum.eth.sync.fastsync.FastSyncState.EMPTY_SYNC_STATE;

import tech.pegasys.pantheon.ethereum.core.BlockHeader;
import tech.pegasys.pantheon.ethereum.core.BlockHeaderTestFixture;
import tech.pegasys.pantheon.ethereum.eth.sync.ChainDownloader;
import tech.pegasys.pantheon.ethereum.eth.sync.TrailingPeerRequirements;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.NodeDataRequest;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.StalledDownloadException;
import tech.pegasys.pantheon.ethereum.eth.sync.worldstate.WorldStateDownloader;
import tech.pegasys.pantheon.services.tasks.TaskCollection;

import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import org.junit.Test;

public class FastSyncDownloaderTest {

  private static final CompletableFuture<FastSyncState> COMPLETE =
      completedFuture(EMPTY_SYNC_STATE);

  @SuppressWarnings("unchecked")
  private final FastSyncActions<Void> fastSyncActions = mock(FastSyncActions.class);

  private final WorldStateDownloader worldStateDownloader = mock(WorldStateDownloader.class);
  private final FastSyncStateStorage storage = mock(FastSyncStateStorage.class);

  @SuppressWarnings("unchecked")
  private final TaskCollection<NodeDataRequest> taskCollection = mock(TaskCollection.class);

  private final ChainDownloader chainDownloader = mock(ChainDownloader.class);

  private final Path fastSyncDataDirectory = null;

  private final FastSyncDownloader<Void> downloader =
      new FastSyncDownloader<>(
          fastSyncActions,
          worldStateDownloader,
          storage,
          taskCollection,
          fastSyncDataDirectory,
          EMPTY_SYNC_STATE);

  @Test
  public void shouldCompleteFastSyncSuccessfully() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(chainDownloader).start();
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);
    assertThat(result).isCompletedWithValue(downloadPivotBlockHeaderState);
  }

  @Test
  public void shouldResumeFastSync() {
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState fastSyncState = new FastSyncState(pivotBlockHeader);
    final CompletableFuture<FastSyncState> complete = completedFuture(fastSyncState);
    when(fastSyncActions.waitForSuitablePeers(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.selectPivotBlock(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.downloadPivotBlockHeader(fastSyncState)).thenReturn(complete);
    when(fastSyncActions.createChainDownloader(fastSyncState)).thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(completedFuture(null));

    final FastSyncDownloader<Void> resumedDownloader =
        new FastSyncDownloader<>(
            fastSyncActions,
            worldStateDownloader,
            storage,
            taskCollection,
            fastSyncDataDirectory,
            fastSyncState);

    final CompletableFuture<FastSyncState> result = resumedDownloader.start();

    verify(fastSyncActions).waitForSuitablePeers(fastSyncState);
    verify(fastSyncActions).selectPivotBlock(fastSyncState);
    verify(fastSyncActions).downloadPivotBlockHeader(fastSyncState);
    verify(storage).storeState(fastSyncState);
    verify(fastSyncActions).createChainDownloader(fastSyncState);
    verify(chainDownloader).start();
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);
    assertThat(result).isCompletedWithValue(fastSyncState);
  }

  @Test
  public void shouldAbortIfWaitForSuitablePeersFails() {
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE))
        .thenReturn(completedExceptionally(new FastSyncException(UNEXPECTED_ERROR)));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, UNEXPECTED_ERROR);

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfSelectPivotBlockFails() {
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenThrow(new FastSyncException(CHAIN_TOO_SHORT));

    final CompletableFuture<FastSyncState> result = downloader.start();

    assertCompletedExceptionally(result, CHAIN_TOO_SHORT);

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verifyNoMoreInteractions(fastSyncActions);
  }

  @Test
  public void shouldAbortIfWorldStateDownloadFails() {
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    assertThat(result).isNotDone();

    worldStateFuture.completeExceptionally(new FastSyncException(NO_PEERS_AVAILABLE));
    verify(chainDownloader).cancel();
    chainFuture.completeExceptionally(new CancellationException());
    assertCompletedExceptionally(result, NO_PEERS_AVAILABLE);
    assertThat(chainFuture).isCancelled();
  }

  @Test
  public void shouldAbortIfChainDownloadFails() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.completeExceptionally(new FastSyncException(NO_PEERS_AVAILABLE));
    assertCompletedExceptionally(result, NO_PEERS_AVAILABLE);
    assertThat(worldStateFuture).isCancelled();
  }

  @Test
  public void shouldNotConsiderFastSyncCompleteIfOnlyWorldStateDownloadIsComplete() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    worldStateFuture.complete(null);
    assertThat(result).isNotDone();
  }

  @Test
  public void shouldNotConsiderFastSyncCompleteIfOnlyChainDownloadIsComplete() {
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final CompletableFuture<Void> worldStateFuture = new CompletableFuture<>();
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(worldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions);
    verifyNoMoreInteractions(worldStateDownloader);

    assertThat(result).isNotDone();

    chainFuture.complete(null);
    assertThat(result).isNotDone();
  }

  @SuppressWarnings("unchecked")
  @Test
  public void shouldResetFastSyncStateAndRestartProcessIfWorldStateIsUnavailable() {
    final CompletableFuture<Void> firstWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> secondWorldStateFuture = new CompletableFuture<>();
    final CompletableFuture<Void> chainFuture = new CompletableFuture<>();
    final ChainDownloader secondChainDownloader = mock(ChainDownloader.class);
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final FastSyncState secondSelectPivotBlockState = new FastSyncState(90);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final BlockHeader secondPivotBlockHeader =
        new BlockHeaderTestFixture().number(90).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    final FastSyncState secondDownloadPivotBlockHeaderState =
        new FastSyncState(secondPivotBlockHeader);
    // First attempt
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(
            completedFuture(selectPivotBlockState), completedFuture(secondSelectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(chainFuture);
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(firstWorldStateFuture);

    // Second attempt with new pivot block
    when(fastSyncActions.downloadPivotBlockHeader(secondSelectPivotBlockState))
        .thenReturn(completedFuture(secondDownloadPivotBlockHeaderState));

    when(fastSyncActions.createChainDownloader(secondDownloadPivotBlockHeaderState))
        .thenReturn(secondChainDownloader);
    when(secondChainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(secondPivotBlockHeader)).thenReturn(secondWorldStateFuture);

    final CompletableFuture<FastSyncState> result = downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(selectPivotBlockState);
    verify(storage).storeState(downloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(downloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(pivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    assertThat(result).isNotDone();

    firstWorldStateFuture.completeExceptionally(new StalledDownloadException("test"));
    assertThat(result).isNotDone();
    verify(chainDownloader).cancel();
    // A real chain downloader would cause the chainFuture to complete when cancel is called.
    chainFuture.completeExceptionally(new CancellationException());

    verify(fastSyncActions, times(2)).waitForSuitablePeers(EMPTY_SYNC_STATE);
    verify(fastSyncActions, times(2)).selectPivotBlock(EMPTY_SYNC_STATE);
    verify(fastSyncActions).downloadPivotBlockHeader(secondSelectPivotBlockState);
    verify(storage).storeState(secondDownloadPivotBlockHeaderState);
    verify(fastSyncActions).createChainDownloader(secondDownloadPivotBlockHeaderState);
    verify(worldStateDownloader).run(secondPivotBlockHeader);
    verifyNoMoreInteractions(fastSyncActions, worldStateDownloader, storage);

    secondWorldStateFuture.complete(null);

    assertThat(result).isCompletedWithValue(secondDownloadPivotBlockHeaderState);
  }

  @Test
  public void shouldNotHaveTrailingPeerRequirementsBeforePivotBlockSelected() {
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE))
        .thenReturn(new CompletableFuture<>());

    downloader.start();

    verify(fastSyncActions).waitForSuitablePeers(EMPTY_SYNC_STATE);
    assertThat(downloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  @Test
  public void shouldNotAllowPeersBeforePivotBlockOnceSelected() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(new CompletableFuture<>());
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(new CompletableFuture<>());

    downloader.start();
    assertThat(downloader.calculateTrailingPeerRequirements())
        .contains(new TrailingPeerRequirements(50, 0));
  }

  @Test
  public void shouldNotHaveTrailingPeerRequirementsAfterDownloadCompletes() {
    final FastSyncState selectPivotBlockState = new FastSyncState(50);
    final BlockHeader pivotBlockHeader = new BlockHeaderTestFixture().number(50).buildHeader();
    final FastSyncState downloadPivotBlockHeaderState = new FastSyncState(pivotBlockHeader);
    when(fastSyncActions.waitForSuitablePeers(EMPTY_SYNC_STATE)).thenReturn(COMPLETE);
    when(fastSyncActions.selectPivotBlock(EMPTY_SYNC_STATE))
        .thenReturn(completedFuture(selectPivotBlockState));
    when(fastSyncActions.downloadPivotBlockHeader(selectPivotBlockState))
        .thenReturn(completedFuture(downloadPivotBlockHeaderState));
    when(fastSyncActions.createChainDownloader(downloadPivotBlockHeaderState))
        .thenReturn(chainDownloader);
    when(chainDownloader.start()).thenReturn(completedFuture(null));
    when(worldStateDownloader.run(pivotBlockHeader)).thenReturn(completedFuture(null));

    final CompletableFuture<FastSyncState> result = downloader.start();
    assertThat(result).isDone();

    assertThat(downloader.calculateTrailingPeerRequirements()).isEmpty();
  }

  private <T> CompletableFuture<T> completedExceptionally(final Throwable error) {
    final CompletableFuture<T> result = new CompletableFuture<>();
    result.completeExceptionally(error);
    return result;
  }

  private <T> void assertCompletedExceptionally(
      final CompletableFuture<T> future, final FastSyncError expectedError) {
    assertThat(future).isCompletedExceptionally();
    future.exceptionally(
        actualError -> {
          assertThat(actualError)
              .isInstanceOf(FastSyncException.class)
              .extracting(ex -> ((FastSyncException) ex).getError())
              .isEqualTo(expectedError);
          return null;
        });
  }
}
