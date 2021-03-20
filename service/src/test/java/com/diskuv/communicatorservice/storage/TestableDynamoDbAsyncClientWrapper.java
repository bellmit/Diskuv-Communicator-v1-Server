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

import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.paginators.QueryPublisher;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.doAnswer;

/**
 * We'll create a mock that delegates to the true client so that we can inject failures. It is
 * essentially a spy, but note that Mockito.spy() won't work because asyncClient instance has a
 * final class. This is cleaner anyway.
 */
public class TestableDynamoDbAsyncClientWrapper {
  private final DynamoDbAsyncClient realAsyncClient;
  private final DynamoDbAsyncClient asyncClient;

  public TestableDynamoDbAsyncClientWrapper(DynamoDbAsyncClient realAsyncClient) {
    this.realAsyncClient = realAsyncClient;
    this.asyncClient = mock(DynamoDbAsyncClient.class);
  }

  public DynamoDbAsyncClient get() {
    return asyncClient;
  }

  public void delegateToRealDynamoDBOperationsExcept(Set<DynamoDBOperation> excludeOperations) {
    reset(asyncClient);

    if (!excludeOperations.contains(DynamoDBOperation.CREATE_TABLE)) {
      doAnswer(o -> realAsyncClient.createTable(o.getArgument(0, CreateTableRequest.class)))
          .when(asyncClient)
          .createTable(any(CreateTableRequest.class));
    }
    if (!excludeOperations.contains(DynamoDBOperation.GET_ITEM)) {
      doAnswer(o -> realAsyncClient.getItem(o.getArgument(0, GetItemRequest.class)))
          .when(asyncClient)
          .getItem(any(GetItemRequest.class));
    }
    if (!excludeOperations.contains(DynamoDBOperation.PUT_ITEM)) {
      doAnswer(o -> realAsyncClient.putItem(o.getArgument(0, PutItemRequest.class)))
          .when(asyncClient)
          .putItem(any(PutItemRequest.class));
    }
    if (!excludeOperations.contains(DynamoDBOperation.UPDATE_ITEM)) {
      doAnswer(o -> realAsyncClient.updateItem(o.getArgument(0, UpdateItemRequest.class)))
          .when(asyncClient)
          .updateItem(any(UpdateItemRequest.class));
    }
    if (!excludeOperations.contains(DynamoDBOperation.QUERY)) {
      doAnswer(o -> realAsyncClient.query(o.getArgument(0, QueryRequest.class)))
          .when(asyncClient)
          .query(any(QueryRequest.class));
    }
    if (!excludeOperations.contains(DynamoDBOperation.QUERY_PAGINATOR)) {
      doAnswer(
              o -> {
                // Without this code, the queryPaginator will delegate all of its .query() to the
                // real
                // client rather than the mock.
                // Otherwise, would be:
                //         return realAsyncClient.queryPaginator(queryRequest);
                QueryRequest queryRequest = o.getArgument(0, QueryRequest.class);
                return new QueryPublisher(asyncClient, queryRequest);
              })
          .when(asyncClient)
          .queryPaginator(any(QueryRequest.class));
    }
  }

  public enum DynamoDBOperation {
    CREATE_TABLE,
    GET_ITEM,
    PUT_ITEM,
    UPDATE_ITEM,
    QUERY,
    QUERY_PAGINATOR
  }
}
