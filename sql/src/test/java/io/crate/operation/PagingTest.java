/*
 * Licensed to Crate.IO GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.operation;

import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class PagingTest {

    @Test
    public void testGetShardPageSize() throws Exception {
        int shardPageSize = Paging.getShardPageSize(10_000, 16);
        assertThat(shardPageSize, is(937));
    }

    @Test
    public void testNumShardsGreaterLimit() throws Exception {
        int shardPageSize = Paging.getShardPageSize(10, 16);
        assertThat(shardPageSize, is(10));
    }

    @Test
    public void testZeroShards() throws Exception {
        int shardPageSize = Paging.getShardPageSize(10, 0);
        assertThat(shardPageSize, is(Paging.DEFAULT_PAGE_SIZE));
    }

    @Test
    public void testZeroShardsOnNode() throws Exception {
        int pageSize = Paging.getNodePageSize(10, 1, 0);
        assertThat(pageSize, is(10));
    }

    @Test
    public void testSmallLimitManyShards() throws Exception {
        int pageSize = Paging.getNodePageSize(10, 20, 5);
        assertThat(pageSize, is(10));
    }

    @Test
    public void testGetNodePageSize() throws Exception {
        int pageSize = Paging.getNodePageSize(10_000, 16, 2);
        assertThat(pageSize, is(1874));
    }
}