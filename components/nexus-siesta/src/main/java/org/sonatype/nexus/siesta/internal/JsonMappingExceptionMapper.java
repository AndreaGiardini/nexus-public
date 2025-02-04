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
package org.sonatype.nexus.siesta.internal;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.sonatype.nexus.rest.ExceptionMapperSupport;
import org.sonatype.nexus.rest.ValidationErrorXO;

import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * Maps {@link JsonMappingExceptionMapper} to {@link Status#BAD_REQUEST} in case there is a deserialization error.
 *
 * @since 3.next
 */
@Named
@Singleton
public class JsonMappingExceptionMapper
    extends ExceptionMapperSupport<JsonMappingException>
{
  @Override
  protected Response convert(final JsonMappingException exception, final String id) {
    final List<ValidationErrorXO> errors = exception.getPath().stream()
        .map(reference -> new ValidationErrorXO(reference.getFieldName(), exception.getOriginalMessage()))
        .collect(Collectors.toList());

    return Response.status(Status.BAD_REQUEST)
        .entity(new GenericEntity<List<ValidationErrorXO>>(errors)
        {
          @Override
          public String toString() {
            return getEntity().toString();
          }
        })
        .type(MediaType.APPLICATION_JSON)
        .build();
  }
}
