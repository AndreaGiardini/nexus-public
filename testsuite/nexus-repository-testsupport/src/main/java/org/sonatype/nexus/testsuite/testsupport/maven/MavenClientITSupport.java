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
package org.sonatype.nexus.testsuite.testsupport.maven;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.ws.rs.core.Response;

import com.sonatype.nexus.docker.testsupport.framework.DockerContainerConfig;
import com.sonatype.nexus.docker.testsupport.maven.MavenCommandLineITSupport;

import org.sonatype.nexus.testsuite.testsupport.FormatClientITSupport;

import com.google.common.base.Joiner;
import org.junit.After;
import org.junit.Before;

import static java.io.File.createTempFile;
import static java.util.UUID.randomUUID;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.forceDelete;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.apache.commons.io.FileUtils.write;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * Support class for Maven Format Client ITs.
 */
public abstract class MavenClientITSupport
    extends FormatClientITSupport
{
  protected static final String PROJECT = "testproject-clientit";

  protected static final String PROJECT_PATH = "/testproject-clientit";

  protected static final String OK_BUILD = "BUILD SUCCESS";

  protected static final String MAVEN_CENTRAL_URL = "https://repo1.maven.org/maven2/";

  protected MavenCommandLineITSupport mvn;

  protected File settings;

  @Before
  public void onInitializeClientIT() throws Exception {
    addTestDataDirectory("target/it-resources/maven");

    createSettingsXml();

    mvn = new MavenCommandLineITSupport(createTestConfig());

    mvn.exec("rm -rf ~/.m2");
  }

  @After
  public void onTearDownClientIT() throws Exception {
    forceDelete(settings);

    mvn.exit();
  }

  private void createSettingsXml() throws Exception {
    settings = createTempFile("settings", ".xml");

    String settingsContent = readFileToString(testData.resolveFile("settings.xml"))
        .replace("${proxyUrl}", getSettingsProxyUrl());

    write(settings, settingsContent);
  }

  protected String buildAndDeployProject(final String url,
                                         final String snapshotVersion,
                                         final boolean success)
  {
    String groupId = randomUUID().toString();
    String artifactId = randomUUID().toString();
    List<String> buildLog = getBuildLog(
        mvn.deploy(PROJECT_PATH,
            url,
            groupId,
            artifactId,
            snapshotVersion
        ));

    assertThat(buildLog.stream().anyMatch(line -> line.contains(OK_BUILD)), is(success));
    return artifactId;
  }

  private List<Map<String, Object>> searchForComponent(final String repository,
                                                       final String artifactId,
                                                       final String version)
      throws Exception
  {
    waitForSearch();

    Response response = restClient().target(nexusUrl("/service/rest/v1/search"))
        .queryParam("repository", repository)
        .queryParam("maven.artifactId", artifactId)
        .queryParam("maven.baseVersion", version)
        .request()
        .buildGet()
        .invoke();

    Map map = response.readEntity(Map.class);
    return (List<Map<String, Object>>) map.get("items");
  }

  protected void verifyComponentExists(final String repositoryName,
                                       final String name,
                                       final String version,
                                       final boolean exists)
      throws Exception
  {
    List<Map<String, Object>> items = searchForComponent(repositoryName, name, version);
    assertThat(items.size(), is(exists ? 1 : 0));
  }

  private String nexusUrl(final String... segments) {
    try {
      return nexusUrl.toURI().resolve(Joiner.on('/').join(Arrays.asList(segments))).toString();
    }
    catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
  }

  protected List<String> getBuildLog(final Optional<List<String>> result) {
    if (!result.isPresent()) {
      throw new AssertionError("No build log found - did the docker container fail to start?");
    }

    return result.get().stream().map(String::trim).collect(toList());
  }

  protected String getSettingsFileLocation() {
    return settings.getAbsolutePath();
  }

  protected abstract DockerContainerConfig createTestConfig() throws Exception;

  protected abstract String getSettingsProxyUrl();
}
