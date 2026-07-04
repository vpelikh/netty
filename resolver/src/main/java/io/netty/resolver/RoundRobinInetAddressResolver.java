/*
 * Copyright 2016 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.resolver;

import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A {@link NameResolver} that resolves {@link InetAddress} and force Round Robin by choosing a single address
 * randomly in {@link #resolve(String)} and {@link #resolve(String, Promise)}
 * if multiple are returned by the {@link NameResolver}.
 * When {@code preferIPv6Addresses} is {@code true}, IPv6 addresses are preferred over IPv4 addresses in both
 * {@link #resolve(String)} and {@link #resolveAll(String)}.
 * Use {@link #asAddressResolver()} to create a {@link InetSocketAddress} resolver
 */
public class RoundRobinInetAddressResolver extends InetNameResolver {
    private final NameResolver<InetAddress> nameResolver;
    private final boolean preferIPv6Addresses;

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned by
     * {@link #resolve(String)}
     * @param nameResolver the {@link NameResolver} used for name resolution
     */
    public RoundRobinInetAddressResolver(EventExecutor executor, NameResolver<InetAddress> nameResolver) {
        this(executor, nameResolver, false);
    }

    /**
     * @param executor the {@link EventExecutor} which is used to notify the listeners of the {@link Future} returned by
     * {@link #resolve(String)}
     * @param nameResolver the {@link NameResolver} used for name resolution
     * @param preferIPv6Addresses if {@code true}, IPv6 addresses will be preferred over IPv4 addresses
     */
    public RoundRobinInetAddressResolver(EventExecutor executor, NameResolver<InetAddress> nameResolver,
                                          boolean preferIPv6Addresses) {
        super(executor);
        this.nameResolver = nameResolver;
        this.preferIPv6Addresses = preferIPv6Addresses;
    }

    @Override
    protected void doResolve(final String inetHost, final Promise<InetAddress> promise) throws Exception {
        // hijack the doResolve request, but do a doResolveAll request under the hood.
        // Note that InetSocketAddress.getHostName() will never incur a reverse lookup here,
        // because an unresolved address always has a host name.
        nameResolver.resolveAll(inetHost).addListener((FutureListener<List<InetAddress>>) future -> {
            if (future.isSuccess()) {
                List<InetAddress> inetAddresses = future.getNow();
                int numAddresses = inetAddresses.size();
                if (numAddresses > 0) {
                    if (preferIPv6Addresses) {
                        // Prefer IPv6 addresses when preferIPv6Addresses is true
                        List<InetAddress> ipv6Addresses = new ArrayList<InetAddress>();
                        List<InetAddress> ipv4Addresses = new ArrayList<InetAddress>();
                        for (InetAddress addr : inetAddresses) {
                            if (addr instanceof Inet6Address) {
                                ipv6Addresses.add(addr);
                            } else {
                                ipv4Addresses.add(addr);
                            }
                        }
                        if (!ipv6Addresses.isEmpty()) {
                            promise.setSuccess(ipv6Addresses.get(randomIndex(ipv6Addresses.size())));
                            return;
                        }
                        if (!ipv4Addresses.isEmpty()) {
                            promise.setSuccess(ipv4Addresses.get(randomIndex(ipv4Addresses.size())));
                            return;
                        }
                    }
                    // if there are multiple addresses: we shall pick one by one
                    // to support the round robin distribution
                    promise.setSuccess(inetAddresses.get(randomIndex(numAddresses)));
                } else {
                    promise.setFailure(new UnknownHostException(inetHost));
                }
            } else {
                promise.setFailure(future.cause());
            }
        });
    }

    @Override
    protected void doResolveAll(String inetHost, final Promise<List<InetAddress>> promise) throws Exception {
        nameResolver.resolveAll(inetHost).addListener((FutureListener<List<InetAddress>>) future -> {
            if (future.isSuccess()) {
                List<InetAddress> inetAddresses = future.getNow();
                if (!inetAddresses.isEmpty()) {
                    List<InetAddress> result;
                    if (preferIPv6Addresses) {
                        // Partition into IPv6 and IPv4 sublists, shuffle each, combine with IPv6 first
                        List<InetAddress> ipv6List = new ArrayList<InetAddress>();
                        List<InetAddress> ipv4List = new ArrayList<InetAddress>();
                        for (InetAddress addr : inetAddresses) {
                            if (addr instanceof Inet6Address) {
                                ipv6List.add(addr);
                            } else {
                                ipv4List.add(addr);
                            }
                        }
                        Collections.shuffle(ipv6List);
                        Collections.shuffle(ipv4List);
                        result = new ArrayList<InetAddress>(
                                ipv6List.size() + ipv4List.size());
                        result.addAll(ipv6List);
                        result.addAll(ipv4List);
                    } else {
                        result = new ArrayList<InetAddress>(inetAddresses);
                        Collections.shuffle(result);
                    }
                    promise.setSuccess(result);
                } else {
                    promise.setSuccess(inetAddresses);
                }
            } else {
                promise.setFailure(future.cause());
            }
        });
    }

    private static int randomIndex(int numAddresses) {
        return numAddresses == 1 ? 0 : ThreadLocalRandom.current().nextInt(numAddresses);
    }

    @Override
    public void close() {
        nameResolver.close();
    }
}
