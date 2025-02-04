/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.testsuite.testsupport.cleanup;

import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.sonatype.nexus.cleanup.storage.CleanupPolicy;
import org.sonatype.nexus.cleanup.storage.CleanupPolicyStorage;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Query;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.scheduling.TaskInfo;
import org.sonatype.nexus.security.subject.FakeAlmightySubject;
import org.sonatype.nexus.testsuite.testsupport.RepositoryITSupport;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.shiro.util.ThreadContext;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.junit.Before;
import org.junit.experimental.categories.Category;

import static com.google.common.collect.Iterables.size;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.rangeQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.joda.time.DateTime.now;
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.IS_PRERELEASE_KEY;
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.LAST_BLOB_UPDATED_KEY;
import static org.sonatype.nexus.repository.search.DefaultComponentMetadataProducer.LAST_DOWNLOADED_KEY;
import static org.sonatype.nexus.scheduling.TaskState.WAITING;

/**
 * Support for cleanup ITs
 */
@Category(CleanupTestGroup.class)
public class CleanupITSupport
    extends RepositoryITSupport
{
  protected static final String TASK_ID = "repository.cleanup";

  protected static final String CLEANUP_MODE = "delete";

  protected static final String POLICY_NAME_KEY = "policyName";

  protected static final String CLEANUP_KEY = "cleanup";

  protected static final int ONE_HUNDRED_SECONDS = 100;

  protected static final int FIFTY_SECONDS = 50;
  
  protected static final int TWO_SECONDS = 2;

  private static final BoolQueryBuilder SEARCH_ALL = boolQuery().must(matchAllQuery());

  @Inject
  private CleanupPolicyStorage cleanupPolicyStorage;

  @Before
  public void setupSearchSecurity() {
    ThreadContext.bind(FakeAlmightySubject.forUserId("disabled-security"));
  }

  protected void setPolicyToBeLastBlobUpdatedInSeconds(final Repository repository,
                                                       final int lastBlobUpdatedSeconds)
      throws Exception
  {
    createOrUpdatePolicyWithCriteria(ImmutableMap.of(LAST_BLOB_UPDATED_KEY, Integer.toString(lastBlobUpdatedSeconds)),
        repository.getFormat().getValue());
    addPolicyToRepository(testName.getMethodName(), repository);
  }

  protected void setPolicyToBeLastDownloadedInSeconds(final Repository repository, final int lastDownloadedSeconds)
      throws Exception
  {
    createOrUpdatePolicyWithCriteria(ImmutableMap.of(LAST_DOWNLOADED_KEY, Integer.toString(lastDownloadedSeconds)),
        repository.getFormat().getValue());
    addPolicyToRepository(testName.getMethodName(), repository);
  }

  protected void setPolicyToBePrerelease(final Repository repository, final boolean prerelease)
      throws Exception
  {
    createOrUpdatePolicyWithCriteria(ImmutableMap.of(IS_PRERELEASE_KEY, Boolean.toString(prerelease)),
        repository.getFormat().getValue());
    addPolicyToRepository(testName.getMethodName(), repository);
  }


  protected void setPolicyToBeMixed(final Repository repository,
                                    final int lastBlobUpdatedSeconds,
                                    final int lastDownloadedSeconds,
                                    final boolean prerelease)
      throws Exception
  {
    createOrUpdatePolicyWithCriteria(ImmutableMap.of(LAST_BLOB_UPDATED_KEY, Integer.toString(lastBlobUpdatedSeconds),
        LAST_DOWNLOADED_KEY, Integer.toString(lastDownloadedSeconds),
        IS_PRERELEASE_KEY, Boolean.toString(prerelease)), repository.getFormat().getValue());

    addPolicyToRepository(testName.getMethodName(), repository);
  }

  protected void createOrUpdatePolicyWithCriteria(final Map<String, String> criteria, final String format) {
    CleanupPolicy existingPolicy = cleanupPolicyStorage.get(testName.getMethodName());

    if (existingPolicy != null) {
      existingPolicy.setCriteria(criteria);
      cleanupPolicyStorage.update(existingPolicy);
    }
    else {
      cleanupPolicyStorage.add(new CleanupPolicy(testName.getMethodName(), "This is a policy for testing", format,
          CLEANUP_MODE, criteria));
    }
  }

  protected void addPolicyToRepository(final String policyName, final Repository repository) throws Exception {
    repository.getConfiguration().getAttributes().put(CLEANUP_KEY, ImmutableMap.of(POLICY_NAME_KEY, policyName));

    repositoryManager.update(repository.getConfiguration());
  }
  
  protected void setLastDownloadedTimes(final String repositoryName, final int minusSeconds) {
    updateAssets(repositoryName, asset -> asset.lastDownloaded(now().minusSeconds(minusSeconds)));
  }

  protected void updateAssets(final String repositoryName, final Consumer<Asset> updater) {
    Repository repository = repositoryManager.get(repositoryName);
    try (StorageTx tx = repository.facet(StorageFacet.class).txSupplier().get()) {
      tx.begin();

      Iterable<Asset> assets = tx.browseAssets(tx.findBucket(repository));

      for (Asset asset : assets) {
        updater.accept(asset);
        tx.saveAsset(asset);
      }

      tx.commit();
    }
  }

  protected void runCleanupTask() throws Exception {
    waitForSearch();
    
    TaskInfo task = findCleanupTask().get();

    // taskScheduler may have beat us to it; only call runNow if we are in WAITING
    waitFor(() -> task.getCurrentState().getState() == WAITING);

    // run the cleanup task and wait for the underlying future to return to ensure completion
    task.runNow();
    task.getCurrentState().getFuture().get();
  }

  protected Optional<TaskInfo> findCleanupTask() {
    return taskScheduler.listsTasks()
        .stream()
        .filter(info -> info.getTypeId().equals(TASK_ID))
        .findFirst();
  }

  protected long countComponents(final String repositoryName) {
    Repository repository = repositoryManager.get(repositoryName);
    try (StorageTx tx = repository.facet(StorageFacet.class).txSupplier().get()) {
      tx.begin();

      return tx.countComponents(Query.builder().where("1").eq("1").build(), ImmutableList.of(repository));
    }
  }

  protected long countAssets(final String repositoryName) {
    Repository repository = repositoryManager.get(repositoryName);
    try (StorageTx tx = repository.facet(StorageFacet.class).txSupplier().get()) {
      tx.begin();

      return tx.countAssets(Query.builder().where("1").eq("1").build(), ImmutableList.of(repository));
    }
  }

  protected void assertLastBlobUpdatedComponentsCleanedUp(final Repository repository,
                                                          final long startingCount,
                                                          final Supplier<Integer> artifactUploader,
                                                          final long expectedCountAfterCleanup) throws Exception
  {
    setPolicyToBeLastBlobUpdatedInSeconds(repository, TWO_SECONDS);
    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(startingCount)));

    //Guarantee that the TWO_SECOND lastBlobUpdated time has passed
    Thread.sleep(3000L);

    int numberUploaded = artifactUploader.get();
    long totalComponents = startingCount + numberUploaded;

    assertThat(countComponents(testName.getMethodName()), is(equalTo(totalComponents)));

    waitFor(() -> size(searchService.browse(SEARCH_ALL)) == totalComponents);

    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(expectedCountAfterCleanup)));
  }

  protected void assertLastDownloadedComponentsCleanedUp(final Repository repository,
                                                         final long startingCount,
                                                         final Supplier<Integer> artifactUploader,
                                                         final long expectedCountAfterCleanup) throws Exception
  {
    setPolicyToBeLastDownloadedInSeconds(repository, FIFTY_SECONDS);
    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(startingCount)));

    setLastDownloadedTimes(testName.getMethodName(), ONE_HUNDRED_SECONDS);

    int numberUploaded = artifactUploader.get();
    long totalComponents = startingCount + numberUploaded;

    assertThat(countComponents(testName.getMethodName()), is(equalTo(totalComponents)));

    waitFor(() -> size(searchService.browse(SEARCH_ALL)) == totalComponents);

    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(expectedCountAfterCleanup)));
  }

  protected void assertCleanupByPrerelease(final Repository repository,
                                           final long countAfterReleaseCleanup,
                                           final long countAfterPrereleaseCleanup) throws Exception
  {
    assertCleanupByPrerelease(repository, countAfterReleaseCleanup, 0L, countAfterPrereleaseCleanup);
  }

  protected void assertCleanupByPrerelease(final Repository repository,
                                           final long countAfterReleaseCleanup,
                                           final long assetCountAfterReleaseCleanup,
                                           final long countAfterPrereleaseCleanup) throws Exception
  {
    long count = countComponents(repository.getName());
    waitFor(() -> size(searchService
        .browseUnrestrictedInRepos(SEARCH_ALL, ImmutableList.of(repository.getName()))) == count);
    
    setPolicyToBePrerelease(repository, true);
    runCleanupTask();

    waitFor(() -> countComponents(testName.getMethodName()) == countAfterPrereleaseCleanup);
    
    setPolicyToBePrerelease(repository, false);
    runCleanupTask();

    waitFor(() -> countComponents(testName.getMethodName()) == countAfterReleaseCleanup);

    waitFor(() -> countAssets(testName.getMethodName()) == assetCountAfterReleaseCleanup);
  }

  protected void assertCleanupByMixedPolicy(final Repository repository,
                                            final long startingCount,
                                            final long countAfterCleanup) throws Exception
  {
    setPolicyToBeMixed(repository, TWO_SECONDS, FIFTY_SECONDS, true);

    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(startingCount)));

    //Guarantee that the TWO_SECOND lastBlobUpdated time has passed
    Thread.sleep(TWO_SECONDS * 1000);

    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(startingCount)));

    setLastDownloadedTimes(testName.getMethodName(), FIFTY_SECONDS);

    waitForMixedSearch();

    runCleanupTask();

    assertThat(countComponents(testName.getMethodName()), is(equalTo(countAfterCleanup)));
  }

  private void waitForMixedSearch() throws Exception {
    BoolQueryBuilder query = boolQuery().must(matchAllQuery());
    
    query.filter(
        rangeQuery(LAST_DOWNLOADED_KEY)
            .lte("now-" + FIFTY_SECONDS + "s")
    ).filter(
        rangeQuery(LAST_BLOB_UPDATED_KEY)
            .lte("now-" + TWO_SECONDS + "s")
    ).must(matchQuery(IS_PRERELEASE_KEY, true));

    waitFor(() -> size(searchService.browseUnrestricted(query)) > 0);
  }

  public static String randomName() {
    return RandomStringUtils.random(5, "abcdefghijklmnopqrstuvwxyz".toCharArray());
  }
}
