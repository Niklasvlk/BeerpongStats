package de.niklasvoelker.beerpongstats.ratelimit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HexFormat;

/**
 * Der Schlüssel, unter dem ein Client gezählt wird. IPv4-Adressen zählen einzeln. Bei IPv6 bekommt jeder Anschluss ein
 * ganzes /64-Netz, deshalb zählt das Netz und nicht die einzelne Adresse; sonst wäre das Limit mit wechselnden
 * Adressen trivial zu umgehen.
 */
final class ClientKey {

    private static final int MAX_LENGTH = 64;

    private ClientKey() {
    }

    static String of(String address) {
        if (address == null || address.isBlank()) {
            return "unknown";
        }
        String trimmed = address.trim();
        // Nur Zeichen, die in IP-Adressen vorkommen. Alles andere wird nicht geparst, denn InetAddress würde bei
        // Hostnamen eine DNS-Anfrage stellen.
        if (!trimmed.matches("[0-9a-fA-F:.]+")) {
            return trimmed.length() > MAX_LENGTH ? trimmed.substring(0, MAX_LENGTH) : trimmed;
        }
        if (!trimmed.contains(":")) {
            return trimmed;
        }
        try {
            byte[] bytes = InetAddress.getByName(trimmed).getAddress();
            // IPv6-Schreibweise einer IPv4-Adresse (::ffff:1.2.3.4) wird zur IPv4-Adresse
            if (bytes.length == 4) {
                return InetAddress.getByAddress(bytes).getHostAddress();
            }
            return HexFormat.of().formatHex(bytes, 0, 8) + "/64";
        } catch (UnknownHostException e) {
            return trimmed;
        }
    }
}
