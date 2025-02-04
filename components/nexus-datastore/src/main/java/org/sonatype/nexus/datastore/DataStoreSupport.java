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
package org.sonatype.nexus.datastore;

import java.util.Map;
import java.util.Map.Entry;

import org.sonatype.nexus.common.stateguard.StateGuardLifecycleSupport;
import org.sonatype.nexus.common.stateguard.Transitions;
import org.sonatype.nexus.datastore.api.DataSession;
import org.sonatype.nexus.datastore.api.DataStore;
import org.sonatype.nexus.datastore.api.DataStoreConfiguration;
import org.sonatype.nexus.transaction.Transaction;

import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toMap;

/**
 * Common support class for {@link DataStore}s.
 *
 * @since 3.next
 */
public abstract class DataStoreSupport<T extends Transaction, S extends DataSession<T>>
    extends StateGuardLifecycleSupport
    implements DataStore<S>
{
  protected DataStoreConfiguration configuration;

  @Override
  public DataStoreConfiguration getConfiguration() {
    return configuration;
  }

  @Override
  public void setConfiguration(final DataStoreConfiguration configuration) {
    this.configuration = checkNotNull(configuration);
  }

  @Override
  protected final void doStart() throws Exception {
    doStart(configuration.getName(), interpolatedAttributes());
  }

  protected abstract void doStart(final String storeName, final Map<String, String> attributes) throws Exception;

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
        "configuration=" + configuration +
        '}';
  }

  /**
   * Interpolate configuration attributes when starting the data store.
   */
  private Map<String, String> interpolatedAttributes() throws Exception {
    Map<String, String> attributes = configuration.getAttributes();

    Interpolator interpolator = new StringSearchInterpolator();
    interpolator.addValueSource(new MapBasedValueSource(attributes));
    interpolator.addValueSource(new MapBasedValueSource(System.getProperties()));
    interpolator.addValueSource(new EnvarBasedValueSource());

    return attributes.entrySet().stream().collect(toMap(Entry::getKey, entry -> {
      try {
        return interpolator.interpolate(entry.getValue());
      }
      catch (InterpolationException e) {
        throw new IllegalArgumentException(e);
      }
    }));
  }

  /**
   * Permanently stops this data store regardless of the current state, disallowing restarts.
   */
  @Override
  @Transitions(to = "SHUTDOWN")
  public void shutdown() throws Exception {
    if (isStarted()) {
      doStop();
    }
  }
}
