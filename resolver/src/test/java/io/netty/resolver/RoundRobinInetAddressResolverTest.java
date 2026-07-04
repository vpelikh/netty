/*
 * Copyright 2026 The Netty Project
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

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.ImmediateEventExecutor;
import io.netty.util.concurrent.Promise;
import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RoundRobinInetAddressResolverTest {

    private static InetAddress ipv4(String addr) throws Exception {
        return InetAddress.getByName(addr);
    }

    private static InetAddress ipv6(String addr) throws Exception {
        return InetAddress.getByName(addr);
    }

    @Test
    public void testResolvePrefersIPv6() throws Exception {
        InetAddress ipv4_1 = ipv4("192.168.1.1");
        InetAddress ipv4_2 = ipv4("192.168.1.2");
        InetAddress ipv6_1 = ipv6("::1");
        InetAddress ipv6_2 = ipv6("::2");

        List<InetAddress> addresses = new ArrayList<InetAddress>();
        addresses.add(ipv4_1);
        addresses.add(ipv6_1);
        addresses.add(ipv4_2);
        addresses.add(ipv6_2);

        @SuppressWarnings("unchecked")
        NameResolver<InetAddress> nameResolver = mock(NameResolver.class);
        Promise<List<InetAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        promise.setSuccess(addresses);
        when(nameResolver.resolveAll(anyString())).thenReturn(promise);

        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(
                ImmediateEventExecutor.INSTANCE, nameResolver, true);

        // Call resolve multiple times - each result should be an IPv6 address
        // (since IPv6 addresses are preferred)
        for (int i = 0; i < 20; i++) {
            Future<InetAddress> future = resolver.resolve("example.com");
            InetAddress result = future.get();
            assertTrue(result instanceof Inet6Address,
                    "Expected IPv6 address but got: " + result.getClass().getSimpleName() + " - " + result);
        }

        resolver.close();
    }

    @Test
    public void testResolveAllOrdersIPv6First() throws Exception {
        InetAddress ipv4_1 = ipv4("192.168.1.1");
        InetAddress ipv4_2 = ipv4("192.168.1.2");
        InetAddress ipv6_1 = ipv6("::1");
        InetAddress ipv6_2 = ipv6("::2");

        List<InetAddress> addresses = new ArrayList<InetAddress>();
        addresses.add(ipv4_1);
        addresses.add(ipv6_1);
        addresses.add(ipv4_2);
        addresses.add(ipv6_2);

        @SuppressWarnings("unchecked")
        NameResolver<InetAddress> nameResolver = mock(NameResolver.class);
        Promise<List<InetAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        promise.setSuccess(addresses);
        when(nameResolver.resolveAll(anyString())).thenReturn(promise);

        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(
                ImmediateEventExecutor.INSTANCE, nameResolver, true);

        Future<List<InetAddress>> future = resolver.resolveAll("example.com");
        List<InetAddress> result = future.get();

        // Verify all 4 addresses are present
        assertEquals(4, result.size());

        // Find the index of the last IPv6 address and first IPv4 address
        int lastIpv6Index = -1;
        int firstIpv4Index = result.size();
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i) instanceof Inet6Address) {
                lastIpv6Index = i;
            } else if (firstIpv4Index == result.size()) {
                firstIpv4Index = i;
            }
        }

        // All IPv6 addresses must appear before any IPv4 address
        assertTrue(lastIpv6Index < firstIpv4Index,
                "IPv6 addresses should come before IPv4 addresses. Last IPv6 at index "
                        + lastIpv6Index + ", first IPv4 at index " + firstIpv4Index);

        resolver.close();
    }

    @Test
    public void testResolveFallbackToIPv4WhenNoIPv6() throws Exception {
        InetAddress ipv4_1 = ipv4("192.168.1.1");
        InetAddress ipv4_2 = ipv4("192.168.1.2");

        List<InetAddress> addresses = new ArrayList<InetAddress>();
        addresses.add(ipv4_1);
        addresses.add(ipv4_2);

        @SuppressWarnings("unchecked")
        NameResolver<InetAddress> nameResolver = mock(NameResolver.class);
        Promise<List<InetAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        promise.setSuccess(addresses);
        when(nameResolver.resolveAll(anyString())).thenReturn(promise);

        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(
                ImmediateEventExecutor.INSTANCE, nameResolver, true);

        // Should fall back to IPv4 when no IPv6 addresses are available
        Future<InetAddress> future = resolver.resolve("example.com");
        InetAddress result = future.get();
        assertTrue(result instanceof Inet4Address,
                "Expected IPv4 address fallback but got: " + result);

        resolver.close();
    }

    @Test
    public void testCloseDelegates() {
        @SuppressWarnings("unchecked")
        NameResolver<InetAddress> nameResolver = mock(NameResolver.class);
        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(
                ImmediateEventExecutor.INSTANCE, nameResolver);
        resolver.close();
        verify(nameResolver, times(1)).close();
    }

    @Test
    public void testBackwardCompatibleOldConstructor() throws Exception {
        InetAddress ipv4_1 = ipv4("192.168.1.1");
        InetAddress ipv4_2 = ipv4("192.168.1.2");
        InetAddress ipv6_1 = ipv6("::1");
        InetAddress ipv6_2 = ipv6("::2");

        List<InetAddress> addresses = new ArrayList<InetAddress>();
        addresses.add(ipv4_1);
        addresses.add(ipv6_1);
        addresses.add(ipv4_2);
        addresses.add(ipv6_2);

        @SuppressWarnings("unchecked")
        NameResolver<InetAddress> nameResolver = mock(NameResolver.class);
        Promise<List<InetAddress>> promise = ImmediateEventExecutor.INSTANCE.newPromise();
        promise.setSuccess(addresses);
        when(nameResolver.resolveAll(anyString())).thenReturn(promise);

        // Use old constructor - should behave as before (no IPv6 preference)
        RoundRobinInetAddressResolver resolver = new RoundRobinInetAddressResolver(
                ImmediateEventExecutor.INSTANCE, nameResolver);

        // resolveAll should return all 4 addresses
        Future<List<InetAddress>> future = resolver.resolveAll("example.com");
        List<InetAddress> result = future.get();
        assertEquals(4, result.size());
        assertTrue(result.contains(ipv4_1), "Should contain IPv4 address");
        assertTrue(result.contains(ipv4_2), "Should contain IPv4 address");
        assertTrue(result.contains(ipv6_1), "Should contain IPv6 address");
        assertTrue(result.contains(ipv6_2), "Should contain IPv6 address");

        // resolve should return both types across multiple calls
        boolean sawIpv4 = false;
        boolean sawIpv6 = false;
        for (int i = 0; i < 20; i++) {
            Future<InetAddress> resolveFuture = resolver.resolve("example.com");
            InetAddress addr = resolveFuture.get();
            if (addr instanceof Inet4Address) {
                sawIpv4 = true;
            } else if (addr instanceof Inet6Address) {
                sawIpv6 = true;
            }
        }
        assertTrue(sawIpv4, "Should see IPv4 addresses with default constructor");
        assertTrue(sawIpv6, "Should see IPv6 addresses with default constructor");

        resolver.close();
    }
}
