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
package io.netty.resolver.dns;

import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.InetSocketAddressResolver;
import io.netty.resolver.NameResolver;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.RoundRobinInetAddressResolver;

import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RoundRobinDnsAddressResolverGroupTest {

    /**
     * Exposes the protected {@code newAddressResolver} method for testing.
     */
    private static final class ExposedGroup extends RoundRobinDnsAddressResolverGroup {
        ExposedGroup() {
            super(NioDatagramChannel.class, DnsServerAddressStreamProviders.platformDefault());
        }

        AddressResolver<InetSocketAddress> callNewAddressResolver(
                EventLoop eventLoop, NameResolver<InetAddress> resolver) throws Exception {
            return newAddressResolver(eventLoop, resolver);
        }
    }

    private static boolean extractPreferIPv6(AddressResolver<InetSocketAddress> addressResolver) throws Exception {
        // InetSocketAddressResolver has a package-private nameResolver field
        Field nameResolverField = InetSocketAddressResolver.class.getDeclaredField("nameResolver");
        nameResolverField.setAccessible(true);
        RoundRobinInetAddressResolver rrResolver =
                (RoundRobinInetAddressResolver) nameResolverField.get(addressResolver);

        Field preferField = RoundRobinInetAddressResolver.class.getDeclaredField("preferIPv6Addresses");
        preferField.setAccessible(true);
        return preferField.getBoolean(rrResolver);
    }

    @Test
    void testNewAddressResolverPrefersIPv6() throws Exception {
        DnsNameResolver mockResolver = mock(DnsNameResolver.class);
        when(mockResolver.resolvedAddressTypes()).thenReturn(ResolvedAddressTypes.IPV6_PREFERRED);

        EventLoop mockEventLoop = mock(EventLoop.class);
        ExposedGroup group = new ExposedGroup();
        AddressResolver<InetSocketAddress> addressResolver =
                group.callNewAddressResolver(mockEventLoop, mockResolver);

        assertTrue(extractPreferIPv6(addressResolver),
                "newAddressResolver should prefer IPv6 when resolvedAddressTypes is IPV6_PREFERRED");
    }

    @Test
    void testNewAddressResolverPrefersIPv6Only() throws Exception {
        DnsNameResolver mockResolver = mock(DnsNameResolver.class);
        when(mockResolver.resolvedAddressTypes()).thenReturn(ResolvedAddressTypes.IPV6_ONLY);

        EventLoop mockEventLoop = mock(EventLoop.class);
        ExposedGroup group = new ExposedGroup();
        AddressResolver<InetSocketAddress> addressResolver =
                group.callNewAddressResolver(mockEventLoop, mockResolver);

        assertTrue(extractPreferIPv6(addressResolver),
                "newAddressResolver should prefer IPv6 when resolvedAddressTypes is IPV6_ONLY");
    }

    @Test
    void testNewAddressResolverNoIPv6Preference() throws Exception {
        DnsNameResolver mockResolver = mock(DnsNameResolver.class);
        when(mockResolver.resolvedAddressTypes()).thenReturn(ResolvedAddressTypes.IPV4_PREFERRED);

        EventLoop mockEventLoop = mock(EventLoop.class);
        ExposedGroup group = new ExposedGroup();
        AddressResolver<InetSocketAddress> addressResolver =
                group.callNewAddressResolver(mockEventLoop, mockResolver);

        assertFalse(extractPreferIPv6(addressResolver),
                "newAddressResolver should not prefer IPv6 when resolvedAddressTypes is IPV4_PREFERRED");
    }

    @Test
    void testNewAddressResolverNonDnsResolver() throws Exception {
        // NameResolver that is not a DnsNameResolver
        NameResolver<InetAddress> mockResolver = mock(NameResolver.class);

        EventLoop mockEventLoop = mock(EventLoop.class);
        ExposedGroup group = new ExposedGroup();
        AddressResolver<InetSocketAddress> addressResolver =
                group.callNewAddressResolver(mockEventLoop, mockResolver);

        assertFalse(extractPreferIPv6(addressResolver),
                "newAddressResolver should not prefer IPv6 when resolver is not a DnsNameResolver");
    }

    @Test
    void testNewAddressResolverPrefersIPv4Only() throws Exception {
        DnsNameResolver mockResolver = mock(DnsNameResolver.class);
        when(mockResolver.resolvedAddressTypes()).thenReturn(ResolvedAddressTypes.IPV4_ONLY);

        EventLoop mockEventLoop = mock(EventLoop.class);
        ExposedGroup group = new ExposedGroup();
        AddressResolver<InetSocketAddress> addressResolver =
                group.callNewAddressResolver(mockEventLoop, mockResolver);

        assertFalse(extractPreferIPv6(addressResolver),
                "newAddressResolver should not prefer IPv6 when resolvedAddressTypes is IPV4_ONLY");
    }
}
