// Copyright 2021 Diskuv, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.diskuv.communicatorservice.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DaoCommons {
  private static final Logger LOGGER = LoggerFactory.getLogger(DaoCommons.class);

  private DaoCommons() {}

  public static CompletableFuture<Boolean> checkIsNotConditionalFailure(CompletableFuture<Void> completionStage, String description) {
    final AtomicBoolean success = new AtomicBoolean(true);
    return completionStage
        .exceptionally(
            throwable -> {
              // re-throw exception, unless it is ConditionalCheckFailedException
              if (throwable instanceof CompletionException) {
                CompletionException completionException = (CompletionException) throwable;
                if (completionException.getCause() instanceof ConditionalCheckFailedException) {
                  LOGGER.warn(
                      String.format("Rejecting %s since it already exists", description),
                      completionException);
                  success.set(false);
                  return null;
                }
              }
              if (throwable instanceof RuntimeException) {
                throw ((RuntimeException) throwable);
              }
              throw new RuntimeException(throwable);
            })
        .thenApply(unused -> success.get());
  }
}
