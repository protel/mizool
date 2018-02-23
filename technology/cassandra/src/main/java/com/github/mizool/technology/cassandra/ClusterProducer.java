/**
 * Copyright 2017-2018 incub8 Software Labs GmbH
 * Copyright 2017-2018 protel Hotelsoftware GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mizool.technology.cassandra;

import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.HostDistance;
import com.datastax.driver.core.PoolingOptions;
import com.datastax.driver.core.QueryOptions;
import com.datastax.driver.extras.codecs.jdk8.InstantCodec;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;
import com.datastax.driver.extras.codecs.jdk8.LocalTimeCodec;
import com.github.mizool.core.exception.ConfigurationException;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

class ClusterProducer
{
    private static final String CASSANDRA_CONTACT_POINTS_PROPERTY_NAME = "cassandra.contactpoints";
    private static final String CASSANDRA_MAX_REQUESTS_PER_CONNECTION_LOCAL = "2048";
    private static final String
        CASSANDRA_MAX_REQUESTS_PER_CONNECTION_LOCAL_PROPERTY_NAME
        = "cassandra.max.requests.per.connection.local";
    private static final String CASSANDRA_MAX_REQUESTS_PER_CONNECTION_REMOTE = "512";
    private static final String
        CASSANDRA_MAX_REQUESTS_PER_CONNECTION_REMOTE_PROPERTY_NAME
        = "cassandra.max.requests.per.connection.remote";

    @Produces
    @Singleton
    public Cluster produce()
    {
        String addresses = System.getProperty(CASSANDRA_CONTACT_POINTS_PROPERTY_NAME);
        if (addresses == null)
        {
            throw new ConfigurationException("No contact points for cassandra set");
        }
        Cluster cluster = Cluster.builder()
            .addContactPoints(parseAddressString(addresses))
            .withQueryOptions(getQueryOptions())
            .withMaxSchemaAgreementWaitSeconds(300)
            .withPoolingOptions(getPoolingOptions())
            .build()
            .init();
        registerJdk8TimeCodecs(cluster);
        return cluster;
    }

    private String[] parseAddressString(String addresses)
    {
        return Iterables.toArray(Splitter.on(",").trimResults().split(addresses), String.class);
    }

    private QueryOptions getQueryOptions()
    {
        QueryOptions queryOptions = new QueryOptions();

        // Default to the safest consistency level in case a store class does not set one
        queryOptions.setConsistencyLevel(ConsistencyLevel.ALL);

        return queryOptions;
    }

    private void registerJdk8TimeCodecs(Cluster cluster)
    {
        cluster.getConfiguration()
            .getCodecRegistry()
            .register(InstantCodec.instance)
            .register(LocalDateCodec.instance)
            .register(LocalTimeCodec.instance);
    }

    private PoolingOptions getPoolingOptions()
    {
        PoolingOptions poolingOptions = new PoolingOptions();
        int maxRequestsPerConnectionLocal = Integer.parseInt(System.getProperty(CASSANDRA_MAX_REQUESTS_PER_CONNECTION_LOCAL_PROPERTY_NAME,
            CASSANDRA_MAX_REQUESTS_PER_CONNECTION_LOCAL));
        int maxRequestsPerConnectionRemote = Integer.parseInt(System.getProperty(CASSANDRA_MAX_REQUESTS_PER_CONNECTION_REMOTE_PROPERTY_NAME,
            CASSANDRA_MAX_REQUESTS_PER_CONNECTION_REMOTE));
        poolingOptions.setMaxRequestsPerConnection(HostDistance.LOCAL, maxRequestsPerConnectionLocal);
        poolingOptions.setMaxRequestsPerConnection(HostDistance.REMOTE, maxRequestsPerConnectionRemote);
        return poolingOptions;
    }

    public void dispose(@Disposes Cluster cluster)
    {
        cluster.close();
    }
}