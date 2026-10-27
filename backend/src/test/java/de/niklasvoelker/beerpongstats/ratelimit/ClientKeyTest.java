package de.niklasvoelker.beerpongstats.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientKeyTest {

    @Test
    void anIpv4AddressCountsOnItsOwn() {
        assertEquals("203.0.113.7", ClientKey.of("203.0.113.7"));
        assertNotEquals(ClientKey.of("203.0.113.7"), ClientKey.of("203.0.113.8"));
    }

    @Test
    void addressesOfTheSameIpv6NetworkShareOneKey() {
        String first = ClientKey.of("2001:db8:1234:5678:aaaa:bbbb:cccc:dddd");
        String second = ClientKey.of("2001:db8:1234:5678:1:2:3:4");

        assertEquals(first, second);
    }

    @Test
    void differentIpv6NetworksAreCountedSeparately() {
        assertNotEquals(ClientKey.of("2001:db8:1234:5678::1"), ClientKey.of("2001:db8:1234:5679::1"));
    }

    @Test
    void theWrittenFormOfAnIpv6AddressDoesNotMatter() {
        assertEquals(ClientKey.of("2001:db8::1"), ClientKey.of("2001:0db8:0000:0000:0000:0000:0000:0001"));
    }

    @Test
    void anIpv4AddressWrittenAsIpv6IsTheSameClient() {
        assertEquals("203.0.113.7", ClientKey.of("::ffff:203.0.113.7"));
    }

    @Test
    void localhostInEitherFormIsUsable() {
        assertEquals("127.0.0.1", ClientKey.of("127.0.0.1"));
        assertNotNull(ClientKey.of("::1"));
        assertNotNull(ClientKey.of("0:0:0:0:0:0:0:1"));
    }

    @Test
    void aMissingAddressGetsAFixedKey() {
        assertEquals("unknown", ClientKey.of(null));
        assertEquals("unknown", ClientKey.of("  "));
    }

    @Test
    void somethingThatIsNoIpAddressIsNotResolved() {
        // Ein Hostname würde bei InetAddress eine DNS-Anfrage auslösen; hier bleibt er ein einfacher Schlüssel
        assertEquals("evil.example.com", ClientKey.of("evil.example.com"));
    }

    @Test
    void garbageIsCutToABoundedLength() {
        assertEquals(64, ClientKey.of("x".repeat(500)).length());
    }

    @Test
    void anInvalidAddressWithIpCharactersStillGivesAKey() {
        assertNotNull(ClientKey.of("1:2:3:4:5:6:7:8:9:10"));
    }
}
