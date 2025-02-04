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
package org.sonatype.nexus.testsuite.testsupport.fixtures

import javax.inject.Provider

import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.config.Configuration
import org.sonatype.nexus.repository.manager.RepositoryManager

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.junit.rules.ExternalResource

import static com.google.common.base.Preconditions.checkNotNull

@Slf4j
@CompileStatic
public class RepositoryRule
    extends ExternalResource
    implements MavenRepoRecipes, RawRepoRecipes, NpmRepoRecipes, PyPiRepoRecipes, AptRepoRecipes, GolangRepoRecipes
{
  final Provider<RepositoryManager> repositoryManagerProvider

  final List<Repository> repositories = []

  RepositoryRule(final Provider<RepositoryManager> repositoryManagerProvider) {
    this.repositoryManagerProvider = checkNotNull(repositoryManagerProvider)
  }

  @Override
  protected void after() {
    repositories.each { Repository repository ->
      log.debug 'Deleting test repository: {}', repository.name
      repositoryManagerProvider.get().delete(repository.name)
    }
    repositories.clear()
  }

  /**
   * Create a repository that will automatically be deleted at the end of a test.
   */
  @Override
  Repository createRepository(final Configuration configuration) {
    log.debug 'Creating and tracking new Repository: {}', configuration.repositoryName
    Repository repository = repositoryManagerProvider.get().create(configuration)
    repositories << repository
    return repository
  }

  /**
   * Delete a Repository previously created by this class.
   */
  void deleteRepository(Repository repository) {
    assert repositories.remove(repository)
    log.debug 'Deleting test repository: {}', repository.name
    repositoryManagerProvider.get().delete(repository.name)
  }
  
}
