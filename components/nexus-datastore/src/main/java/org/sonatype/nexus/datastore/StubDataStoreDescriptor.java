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

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.goodies.i18n.I18N;
import org.sonatype.goodies.i18n.MessageBundle;
import org.sonatype.nexus.formfields.FormField;
import org.sonatype.nexus.formfields.StringTextFormField;

import static java.util.Arrays.asList;
import static org.sonatype.nexus.formfields.FormField.MANDATORY;

/**
 * Temporary stubbed {@link DataStoreDescriptor}.
 *
 * @since 3.next
 */
@Named("jdbc")
public class StubDataStoreDescriptor
    implements DataStoreDescriptor
{
  private interface Messages
      extends MessageBundle
  {
    @DefaultMessage("JDBC")
    String name();

    @DefaultMessage("URL")
    String urlLabel();
  }

  private static final Messages messages = I18N.create(Messages.class);

  private final FormField<?> jdbcUrl;

  @Inject
  public StubDataStoreDescriptor() {
    this.jdbcUrl = new StringTextFormField(
        "jdbcUrl",
        messages.urlLabel(),
        null,
        MANDATORY
    );
  }

  @Override
  public String getName() {
    return messages.name();
  }

  @Override
  public List<FormField<?>> getFormFields() {
    return asList(jdbcUrl);
  }
}
