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
package org.sonatype.nexus.testsuite.testsupport;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;

import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.config.Configuration;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.search.SearchService;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.security.SecuritySystem;
import org.sonatype.nexus.security.authc.apikey.ApiKeyStore;
import org.sonatype.nexus.security.authz.AuthorizationManager;
import org.sonatype.nexus.security.authz.NoSuchAuthorizationManagerException;
import org.sonatype.nexus.security.realm.RealmConfiguration;
import org.sonatype.nexus.security.realm.RealmManager;
import org.sonatype.nexus.security.role.NoSuchRoleException;
import org.sonatype.nexus.security.role.Role;
import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.NoSuchUserManagerException;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;
import org.sonatype.nexus.selector.SelectorConfiguration;
import org.sonatype.nexus.selector.SelectorManager;
import org.sonatype.nexus.testsuite.testsupport.apt.AptClient;
import org.sonatype.nexus.testsuite.testsupport.apt.AptClientFactory;
import org.sonatype.nexus.testsuite.testsupport.fixtures.RepositoryRule;
import org.sonatype.nexus.testsuite.testsupport.golang.GolangClient;
import org.sonatype.nexus.testsuite.testsupport.npm.NpmClient;
import org.sonatype.nexus.testsuite.testsupport.raw.RawClient;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.auth.Credentials;
import org.apache.http.util.EntityUtils;
import org.hamcrest.MatcherAssert;
import org.joda.time.DateTime;
import org.junit.Rule;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;
import static org.hamcrest.Matchers.is;
import static org.sonatype.nexus.repository.http.HttpStatus.OK;
import static org.sonatype.nexus.repository.storage.MetadataNodeEntityAdapter.P_NAME;
import static org.sonatype.nexus.security.user.UserManager.DEFAULT_SOURCE;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.status;

/**
 * Support class for repository format ITs.
 */
public abstract class GenericRepositoryITSupport<RR extends RepositoryRule>
    extends NexusITSupport
{
  protected static final String SLASH_REPO_SLASH = "/repository/";

  protected static final int MAX_NUGET_CLIENT_CONNECTIONS = 100;

  protected AptClientFactory aptClientFactory = new AptClientFactory();

  @Inject
  protected RepositoryManager repositoryManager;

  @Inject
  protected SearchService searchService;

  @Inject
  protected ApiKeyStore keyStore;

  @Inject
  protected RealmManager realmManager;

  @Inject
  protected SecuritySystem securitySystem;

  @Inject
  protected SelectorManager selectorManager;

  @Rule
  public RR repos = createRepositoryRule();

  protected abstract RR createRepositoryRule();

  @Nonnull
  protected URL repositoryBaseUrl(final Repository repository) {
    return resolveUrl(nexusUrl, SLASH_REPO_SLASH + repository.getName() + "/");
  }

  @Nonnull
  protected RawClient rawClient(final Repository repository) throws Exception {
    checkNotNull(repository);
    return rawClient(repositoryBaseUrl(repository));
  }

  protected RawClient rawClient(final URL repositoryUrl) throws Exception {
    return new RawClient(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }

  protected NpmClient createNpmClient(final Repository repository, URL overrideNexusUrl) throws Exception {
    return createNpmClient(resolveUrl(overrideNexusUrl, SLASH_REPO_SLASH + repository.getName() + "/"));
  }

  protected NpmClient createNpmClient(final Repository repository) throws Exception {
    return createNpmClient(repository, nexusUrl);
  }

  protected NpmClient createNpmClient(final URL repositoryUrl) throws Exception {
    return new NpmClient(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI(),
        testData
    );
  }

  protected GolangClient createGolangClient(final Repository repository) throws Exception {
    checkNotNull(repository);
    return createGolangClient(repositoryBaseUrl(repository));
  }

  protected GolangClient createGolangClient(final URL repositoryUrl) throws Exception {
    return new GolangClient(
        clientBuilder(repositoryUrl).build(),
        clientContext(),
        repositoryUrl.toURI()
    );
  }

  protected AptClient createAptClient(final String repositoryName) throws Exception {
    Credentials creds = credentials();
    return createAptClient(repositoryName, creds.getUserPrincipal().getName(), creds.getPassword());
  }

  protected AptClient createAptClient(final String repositoryName, final String username, final String password)
  {
    checkNotNull(repositoryManager.get(repositoryName));
    return aptClientFactory
        .createClient(resolveUrl(nexusUrl, SLASH_REPO_SLASH + repositoryName + "/"), username, password);
  }

  protected void enableRealm(final String realmName) {
    log.info("Realm configuration: {}", realmManager.getConfiguration());

    final RealmConfiguration config = realmManager.getConfiguration();

    if (!config.getRealmNames().contains(realmName)) {

      log.info("Adding {}.", realmName);

      config.getRealmNames().add(realmName);
      realmManager.setConfiguration(config);
    }
    else {
      log.info("{} realm already configured.", realmName);
    }
  }

  /**
   * Waits for indexing to finish and makes sure any updates are available to search.
   *
   * General flow is component/asset events -> bulk index requests -> search indexing.
   */
  protected void waitForSearch() throws Exception {
    waitFor(eventManager::isCalmPeriod);
    searchService.flush(false); // no need for full fsync here
    waitFor(searchService::isCalmPeriod);
  }

  protected void maybeCreateUser(final String username, final String password, final String role)
      throws NoSuchUserManagerException
  {
    try {
      User testUser = securitySystem.getUser(username);
      securitySystem.updateUser(userSetRoles(testUser, role));
    }
    catch (UserNotFoundException e) { // NOSONAR
      securitySystem.addUser(createUser(username, role), password);
    }
  }

  protected static Role createRole(final String name, final String... privileges) {
    Role role = new Role();
    role.setRoleId(name);
    role.setSource(DEFAULT_SOURCE);
    role.setName(name);
    role.setDescription(name);
    role.setReadOnly(false);
    role.setPrivileges(Sets.newHashSet(privileges));
    return role;
  }

  protected static User userSetRoles(final User user, final String... roles) {
    Set<RoleIdentifier> roleIds = Arrays.stream(roles)
        .map(r -> new RoleIdentifier(DEFAULT_SOURCE, r))
        .collect(Collectors.toSet());
    user.setRoles(roleIds);
    return user;
  }

  protected static User createUser(final String username, final String role) {
    User user = new User();
    user.setUserId(username);
    user.setSource(DEFAULT_SOURCE);
    user.setFirstName(username);
    user.setLastName(username);
    user.setEmailAddress("void@void.void");
    user.setStatus(UserStatus.active);
    return userSetRoles(user, role);
  }

  protected void maybeCreateSelector(final String name, final String type, final String expression) {
    if (selectorManager.browse().stream().noneMatch(s -> name.equals(s.getName()))) {
      SelectorConfiguration config = new SelectorConfiguration();
      config.setName(name);
      config.setDescription(name);
      config.setType(type);
      config.setAttributes(ImmutableMap.of("expression", expression));
      selectorManager.create(config);
    }
  }

  protected void maybeCreateRole(final String name, final String... privileges)
      throws NoSuchAuthorizationManagerException
  {
    AuthorizationManager aznManager = securitySystem.getAuthorizationManager(DEFAULT_SOURCE);
    try {
      aznManager.getRole(name);
    }
    catch (NoSuchRoleException e) { // NOSONAR
      aznManager.addRole(createRole(name, privileges));
    }
  }

  protected DateTime getLastDownloadedTime(final Repository repository, final String assetName) {
    try (StorageTx tx = repository.facet(StorageFacet.class).txSupplier().get()) {
      tx.begin();

      Iterable<Asset> assets = tx.browseAssets(tx.findBucket(repository));
      for (Asset asset : assets) {
        if (asset.name().equals(assetName)) {
          return asset.lastDownloaded();
        }
      }

      return null;
    }
  }

  protected static Component findComponent(final Repository repo, final String name) {
    try (StorageTx tx = getStorageTx(repo)) {
      tx.begin();
      return tx.findComponentWithProperty(P_NAME, name, tx.findBucket(repo));
    }
  }

  protected static List<Component> getAllComponents(final Repository repo) {
    try (StorageTx tx = repo.facet(StorageFacet.class).txSupplier().get()) {
      tx.begin();

      return newArrayList(tx.browseComponents(tx.findBucket(repo)));
    }
  }

  protected static Asset findAsset(final Repository repo, final String name) {
    try (StorageTx tx = getStorageTx(repo)) {
      tx.begin();
      return tx.findAssetWithProperty(P_NAME, name, tx.findBucket(repo));
    }
  }

  protected static StorageTx getStorageTx(final Repository repository) {
    return repository.facet(StorageFacet.class).txSupplier().get();
  }

  /**
   * Sets the content max age and metadata max age on a proxy repository.
   */
  protected void setContentAndMetadataMaxAge(final Repository proxyRepository,
                                             final int contentMaxAge,
                                             final int metadataMaxAge) throws Exception
  {
    Configuration configuration = proxyRepository.getConfiguration();
    configuration.attributes("proxy").set("contentMaxAge", Integer.toString(contentMaxAge));
    configuration.attributes("proxy").set("metadataMaxAge", Integer.toString(metadataMaxAge));
    repositoryManager.update(configuration);
  }

  protected void assertSuccessResponseMatches(final HttpResponse response, final String expectedFile)
      throws IOException
  {
    MatcherAssert.assertThat(status(response), is(OK));
    byte[] fetchedDeb = EntityUtils.toByteArray(response.getEntity());
    try (FileInputStream file = new FileInputStream(testData.resolveFile(expectedFile))) {
      byte[] expectedDeb = IOUtils.toByteArray(file);
      MatcherAssert.assertThat(Arrays.equals(fetchedDeb, expectedDeb), is(true));
    }
  }
}
